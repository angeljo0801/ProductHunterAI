# Product Hunter AI v0.2.0 — datos reales

Android-first product research app for Shopify/e-commerce product discovery and validation.

## Qué cambió en v0.2.0

La pestaña **Descubrir** ahora hace investigación en vivo. La app separa claramente:

- **evidencia real recuperada de una fuente**;
- **señales/estimaciones derivadas de esa evidencia**;
- **datos financieros introducidos por el usuario**.

No inventa ventas de competidores ni presenta un score como garantía de éxito.

## Fuentes conectadas

### Sin credenciales

- **Google Trends — Trending Now RSS**: consulta el feed público por país y busca coincidencias con el producto.
- **TikTok Creative Center**: mantiene acceso al recurso público y abre la fuente oficial. La automatización completa es limitada porque TikTok no ofrece una API pública estable para todo Creative Center.

### Con credenciales opcionales

- **Google Shopping vía SerpAPI**: precios, vendedor/fuente, rating, número de reseñas y enlaces.
- **Amazon vía SerpAPI**: resultados, precios, ratings, reviews y ASIN.
- **Amazon Product Reviews via SerpAPI**: para los primeros ASIN consulta `reviews_information`, temas de reseñas, sentimiento y menciones negativas/mixtas.
- **TikTok evidence via SerpAPI Google Search**: busca referencias públicas indexadas de Creative Center.
- **Reddit OAuth**: busca discusiones públicas relevantes usando la API autorizada.
- **Shopify Admin GraphQL API 2026-10**: consulta productos relacionados dentro de tu propia tienda. El token necesita `read_products`.

## Market Signal Score

El score de investigación en vivo es distinto al Product Score manual. Resume:

- número de fuentes en vivo;
- cantidad de evidencia;
- volumen agregado de reseñas/comentarios disponible;
- amplitud de precios observados.

También deriva señales de 0–10 para:

- demanda;
- saturación;
- evidencia de problema;
- potencial social/video;
- confianza de la investigación.

Estas señales son heurísticas transparentes, no predicciones de ventas.

## Configuración desde Android

Abre:

**Herramientas → Fuentes y conectores**

Campos disponibles:

- País (`US`, `MX`, `ES`, etc.)
- SerpAPI key
- Reddit OAuth bearer token
- Reddit username para User-Agent
- Shopify store domain (`tu-tienda.myshopify.com`)
- Shopify Admin API access token
- Backend URL opcional

Después ve a **Descubrir**, escribe un producto y pulsa **Investigar ahora**.

## Modo directo vs backend

### Modo directo

Si `Backend URL` está vacío, el teléfono consulta las fuentes directamente. Es práctico para desarrollo personal.

### Backend seguro

La carpeta `backend/` incluye una API FastAPI. Es la opción recomendada si vas a distribuir el APK: las claves se guardan como variables de entorno en el servidor y no dentro del cliente móvil.

Endpoints:

- `GET /health`
- `GET /research?q=portable+car+vacuum&country=US`

Variables de entorno disponibles:

```text
SERPAPI_KEY=
REDDIT_BEARER_TOKEN=
REDDIT_USERNAME=
SHOPIFY_STORE_DOMAIN=your-store.myshopify.com
SHOPIFY_ADMIN_TOKEN=
```

### Ejecutar backend

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

También se incluye `Dockerfile` y `railway.toml` para despliegue.

## Funciones que siguen en la app

- Product Score /100 manual
- análisis financiero
- CPA máximo y ROAS de equilibrio
- analizador local de reseñas pegadas
- comparación de productos
- biblioteca persistente
- generador de hipótesis por nicho/problema

## Seguridad

La configuración directa se guarda localmente para esta versión de desarrollo. No distribuyas un APK con un Shopify Admin token u otras credenciales sensibles configuradas. Para producción, usa el backend.

## Estado de compilación en este paquete

Se validaron:

- sintaxis Python del backend (`py_compile`);
- núcleo Kotlin puro de scoring, finanzas y señales de mercado con `kotlinc`.

El entorno de creación de este paquete no incluye Android SDK/Gradle para producir un APK firmado; abre el proyecto en Android Studio o usa tu pipeline de GitHub Actions para generar el APK.

## CI en GitHub

El repositorio incluye dos workflows:

- **Build Android APK**: instala Java 17 y Gradle 8.11.1 en el runner, compila `:app:assembleDebug` y publica `ProductHunterAI-debug` como artifact.
- **Check backend**: instala dependencias Python, valida sintaxis e importa la app FastAPI.

No se requiere guardar el Gradle Wrapper dentro del repositorio para este pipeline porque el workflow instala una versión fija de Gradle mediante `gradle/actions/setup-gradle`.
