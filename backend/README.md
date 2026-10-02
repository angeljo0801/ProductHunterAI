# Product Hunter AI backend

Optional secure proxy for Product Hunter AI. Keep API keys here instead of shipping them inside a mobile APK.

## Run locally

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# export the variables or load .env with your deployment platform
uvicorn main:app --host 0.0.0.0 --port 8000
```

Endpoints:

- `GET /health`
- `GET /research?q=portable+car+vacuum&country=US`

The Android app can point to this server from **Herramientas → Fuentes y conectores → Backend seguro**.
