import os
import re
import statistics
import time
from typing import Any
from urllib.parse import quote_plus

import requests
from fastapi import FastAPI, HTTPException, Query

app = FastAPI(title="Product Hunter AI Backend", version="0.2.0")
UA = "ProductHunterAI-Backend/0.2"


def get_json(url: str, headers: dict[str, str] | None = None) -> dict[str, Any]:
    r = requests.get(url, headers={"User-Agent": UA, **(headers or {})}, timeout=20)
    r.raise_for_status()
    return r.json()


def get_text(url: str, headers: dict[str, str] | None = None) -> str:
    r = requests.get(url, headers={"User-Agent": UA, **(headers or {})}, timeout=20)
    r.raise_for_status()
    return r.text


def state_report(source: str, state: str, summary: str, evidence=None, error=None):
    return {"source": source, "state": state, "summary": summary, "evidence": evidence or [], "error": error}


def google_trends(query: str, country: str):
    try:
        xml = get_text(f"https://trends.google.com/trending/rss?geo={quote_plus(country)}")
        items = re.findall(r"<item>(.*?)</item>", xml, re.S | re.I)
        rows = []
        for block in items[:30]:
            mt = re.search(r"<title>(.*?)</title>", block, re.S | re.I)
            if not mt:
                continue
            title = re.sub(r"<!\[CDATA\[(.*?)\]\]>", r"\1", mt.group(1)).strip()
            traffic_m = re.search(r"<(?:ht:)?approx_traffic>(.*?)</(?:ht:)?approx_traffic>", block, re.S | re.I)
            traffic = traffic_m.group(1).strip() if traffic_m else ""
            rows.append({
                "source": "Google Trends",
                "title": title,
                "detail": f"Volumen aprox.: {traffic}" if traffic else "Trending Now",
                "url": f"https://trends.google.com/trending?geo={country}",
            })
        tokens = [t for t in query.lower().split() if len(t) >= 3]
        matching = [x for x in rows if any(t in x["title"].lower() for t in tokens)]
        return state_report(
            "Google Trends",
            "LIVE",
            f"{len(matching)} coincidencia(s) en Trending Now." if matching else "Fuente en vivo; el término no aparece en los picos recientes.",
            matching or rows[:5],
        )
    except Exception as e:
        return state_report("Google Trends", "ERROR", "No se pudo leer Trending Now.", error=str(e)[:220])


def serp_google_shopping(query: str, country: str):
    key = os.getenv("SERPAPI_KEY", "").strip()
    if not key:
        return state_report("Google Shopping", "NEEDS_AUTH", "Configura SERPAPI_KEY en el backend.")
    try:
        data = get_json(
            f"https://serpapi.com/search.json?engine=google_shopping&q={quote_plus(query)}&gl={quote_plus(country.lower())}&hl=en&api_key={quote_plus(key)}"
        )
        arr = data.get("shopping_results") or data.get("inline_shopping_results") or []
        evidence = []
        for o in arr[:20]:
            evidence.append({
                "source": "Google Shopping",
                "title": o.get("title") or "Producto",
                "detail": " • ".join(str(v) for v in [o.get("source"), o.get("price"), f"{o.get('reviews')} reseñas" if o.get("reviews") is not None else None] if v),
                "url": o.get("product_link") or o.get("link") or "",
                "price": o.get("extracted_price"),
                "rating": o.get("rating"),
                "reviews": o.get("reviews"),
            })
        return state_report("Google Shopping", "LIVE", f"{len(evidence)} resultados comerciales reales.", evidence)
    except Exception as e:
        return state_report("Google Shopping", "ERROR", "Falló Google Shopping.", error=str(e)[:220])


def serp_amazon(query: str):
    key = os.getenv("SERPAPI_KEY", "").strip()
    if not key:
        return state_report("Amazon", "NEEDS_AUTH", "Configura SERPAPI_KEY en el backend.")
    try:
        data = get_json(
            f"https://serpapi.com/search.json?engine=amazon&k={quote_plus(query)}&amazon_domain=amazon.com&language=en_US&api_key={quote_plus(key)}"
        )
        arr = data.get("organic_results") or []
        product_evidence = []
        for o in arr[:20]:
            price = None
            if isinstance(o.get("price"), dict):
                price = o["price"].get("extracted_price")
            price = price if price is not None else o.get("extracted_price")
            reviews = o.get("reviews") if isinstance(o.get("reviews"), int) else o.get("reviews_count")
            detail_bits = []
            if price is not None:
                detail_bits.append(f"${price}")
            if o.get("rating") is not None:
                detail_bits.append(f"{o.get('rating')}★")
            if reviews is not None:
                detail_bits.append(f"{reviews} reseñas")
            product_evidence.append({
                "source": "Amazon",
                "title": o.get("title") or "Producto Amazon",
                "detail": " • ".join(detail_bits),
                "url": o.get("link") or "",
                "price": price,
                "rating": o.get("rating"),
                "reviews": reviews,
            })

        review_evidence = []
        asins = [o.get("asin") for o in arr[:2] if o.get("asin")]
        for asin in asins:
            try:
                product = get_json(
                    f"https://serpapi.com/search.json?engine=amazon_product&asin={quote_plus(str(asin))}&amazon_domain=amazon.com&api_key={quote_plus(key)}"
                )
                info = product.get("reviews_information") or {}
                summary = info.get("summary") or {}
                for insight in (summary.get("insights") or [])[:6]:
                    sentiment = str(insight.get("sentiment") or "unknown")
                    mentions = insight.get("mentions") or {}
                    negative = int(mentions.get("negative") or 0)
                    total = int(mentions.get("total") or 0)
                    if sentiment.lower() == "positive" and negative == 0:
                        continue
                    examples = insight.get("examples") or []
                    review_evidence.append({
                        "source": "Amazon Review Insight",
                        "title": insight.get("title") or "Tema de reseñas",
                        "detail": " • ".join(x for x in [f"Sentimiento: {sentiment}", f"{negative} negativas de {total} menciones" if total else "", insight.get("summary") or ""] if x),
                        "url": (examples[0].get("link") if examples and isinstance(examples[0], dict) else "") or "",
                        "reviews": negative or (total if total else None),
                    })
            except Exception:
                pass
        evidence = product_evidence + review_evidence
        return state_report("Amazon", "LIVE", f"{len(product_evidence)} competidores y {len(review_evidence)} temas de reseñas.", evidence)
    except Exception as e:
        return state_report("Amazon", "ERROR", "Falló Amazon.", error=str(e)[:220])


def tiktok(query: str, country: str):
    key = os.getenv("SERPAPI_KEY", "").strip()
    official = f"https://ads.tiktok.com/business/creativecenter/inspiration/topads/pc/en?region={country}"
    if not key:
        return state_report(
            "TikTok Creative Center",
            "LIMITED",
            "Creative Center es público, pero no ofrece una API pública estable para automatizar todo Top Products/Top Ads.",
            [{"source": "TikTok Creative Center", "title": "Abrir Creative Center", "detail": "Top Ads y tendencias públicas", "url": official}],
        )
    try:
        q = quote_plus(f"site:ads.tiktok.com/business/creativecenter {query}")
        data = get_json(f"https://serpapi.com/search.json?engine=google&q={q}&gl={country.lower()}&hl=en&api_key={quote_plus(key)}")
        arr = data.get("organic_results") or []
        evidence = [{
            "source": "TikTok Creative Center",
            "title": o.get("title") or "TikTok",
            "detail": o.get("snippet") or "",
            "url": o.get("link") or official,
        } for o in arr[:10]]
        return state_report("TikTok Creative Center", "LIVE" if evidence else "LIMITED", f"{len(evidence)} referencias públicas indexadas." if evidence else "Sin evidencia indexada; abre Creative Center.", evidence or [{"source": "TikTok Creative Center", "title": "Abrir Creative Center", "url": official}])
    except Exception as e:
        return state_report("TikTok Creative Center", "ERROR", "Falló la consulta de evidencia pública.", error=str(e)[:220])


def reddit(query: str):
    token = os.getenv("REDDIT_BEARER_TOKEN", "").strip()
    username = os.getenv("REDDIT_USERNAME", "producthunter_user").strip()
    if not token:
        return state_report("Reddit", "NEEDS_AUTH", "Configura REDDIT_BEARER_TOKEN; Reddit exige OAuth.")
    try:
        headers = {"Authorization": f"Bearer {token}", "User-Agent": f"web:producthunter.ai:v0.2 (by /u/{username})"}
        data = get_json(f"https://oauth.reddit.com/search?q={quote_plus(query)}&sort=relevance&t=year&limit=25&type=link", headers)
        children = ((data.get("data") or {}).get("children") or [])[:20]
        evidence = []
        for child in children:
            d = child.get("data") or {}
            evidence.append({
                "source": "Reddit",
                "title": d.get("title") or "Post",
                "detail": f"r/{d.get('subreddit','')} • {d.get('score',0)} pts • {d.get('num_comments',0)} comentarios",
                "url": "https://www.reddit.com" + (d.get("permalink") or ""),
                "reviews": d.get("num_comments") or 0,
            })
        return state_report("Reddit", "LIVE", f"{len(evidence)} discusiones públicas vía OAuth.", evidence)
    except Exception as e:
        return state_report("Reddit", "ERROR", "Falló Reddit OAuth.", error=str(e)[:220])


def shopify(query: str):
    domain = os.getenv("SHOPIFY_STORE_DOMAIN", "").strip().replace("https://", "").replace("http://", "").rstrip("/")
    token = os.getenv("SHOPIFY_ADMIN_TOKEN", "").strip()
    if not domain or not token:
        return state_report("Tu Shopify", "NEEDS_AUTH", "Configura SHOPIFY_STORE_DOMAIN y SHOPIFY_ADMIN_TOKEN.")
    try:
        endpoint = f"https://{domain}/admin/api/2026-10/graphql.json"
        safe = query.replace("\\", "\\\\").replace('"', '\\"')
        gql = f'''query {{ products(first: 10, query: "{safe}") {{ nodes {{ id title handle vendor productType variants(first: 3) {{ nodes {{ title price }} }} }} }} }}'''
        r = requests.post(endpoint, headers={"User-Agent": UA, "Content-Type": "application/json", "X-Shopify-Access-Token": token}, json={"query": gql}, timeout=20)
        r.raise_for_status()
        data = r.json()
        nodes = (((data.get("data") or {}).get("products") or {}).get("nodes") or [])
        evidence = []
        for o in nodes:
            vnodes = ((o.get("variants") or {}).get("nodes") or [])
            first = vnodes[0] if vnodes else {}
            price = None
            try:
                price = float(first.get("price")) if first.get("price") else None
            except Exception:
                pass
            evidence.append({
                "source": "Tu Shopify",
                "title": o.get("title") or "Producto",
                "detail": " • ".join(v for v in [o.get("vendor") or "", o.get("productType") or "", first.get("price") or ""] if v),
                "url": f"https://{domain}/products/{o.get('handle','')}",
                "price": price,
            })
        return state_report("Tu Shopify", "LIVE", f"{len(evidence)} producto(s) relacionados en tu catálogo.", evidence)
    except Exception as e:
        return state_report("Tu Shopify", "ERROR", "Falló Shopify. Revisa dominio, token y read_products.", error=str(e)[:220])


def aggregate(query: str, reports: list[dict[str, Any]]):
    evidence = [e for r in reports for e in r.get("evidence", [])]
    prices = sorted(float(e["price"]) for e in evidence if isinstance(e.get("price"), (int, float)) and float(e["price"]) > 0)
    review_count = sum(int(e.get("reviews") or 0) for e in evidence)
    live_sources = sum(1 for r in reports if r.get("state") == "LIVE")
    evidence_score = int(min(len(evidence), 40) / 40 * 35)
    source_score = int(min(live_sources, 5) / 5 * 25)
    import math
    review_score = int(max(0, min(25, (math.log(review_count + 1) / math.log(100001)) * 25))) if review_count >= 0 else 0
    price_score = 15 if len(prices) >= 5 else len(prices) * 3
    signal = max(0, min(100, evidence_score + source_score + review_score + price_score))
    return {
        "query": query,
        "generated_at": int(time.time() * 1000),
        "reports": reports,
        "market_price_median": statistics.median(prices) if prices else None,
        "market_price_min": prices[0] if prices else None,
        "market_price_max": prices[-1] if prices else None,
        "review_count_signal": review_count,
        "evidence_count": len(evidence),
        "market_signal_score": signal,
        "caveats": [
            "Market Signal Score mide evidencia disponible; no predice ventas.",
            "Trending Now detecta picos recientes y no reemplaza un estudio de demanda estable.",
            "TikTok puede requerir acceso manual o un proveedor autorizado adicional para datos completos.",
        ],
    }


@app.get("/health")
def health():
    return {
        "ok": True,
        "version": "0.2.0",
        "connectors": {
            "google_trends": True,
            "serpapi": bool(os.getenv("SERPAPI_KEY")),
            "reddit": bool(os.getenv("REDDIT_BEARER_TOKEN")),
            "shopify": bool(os.getenv("SHOPIFY_STORE_DOMAIN") and os.getenv("SHOPIFY_ADMIN_TOKEN")),
        },
    }


@app.get("/research")
def research(q: str = Query(min_length=2, max_length=160), country: str = Query(default="US", min_length=2, max_length=3)):
    country = country.upper()
    reports = [
        google_trends(q, country),
        serp_google_shopping(q, country),
        serp_amazon(q),
        tiktok(q, country),
        reddit(q),
        shopify(q),
    ]
    return aggregate(q, reports)
