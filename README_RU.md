# ai-proxy — универсальный AI Gateway

Пришло время оформить это как цельный документ. Ниже — назначение, аналоги, конфиги, интеграция с OpenCode, примеры запросов и ответ по устойчивости настроек при рестарте.

## Зачем нужен ai-proxy

`ai-proxy` — это лёгкий self-hosted reverse proxy, который даёт единую точку доступа к нескольким AI-провайдерам (Gemini, OpenAI, Anthropic и любым другим с HTTP REST API), решая три задачи:

- **Обход сетевых ограничений** — запрос уходит с сервера в разрешённой юрисдикции/сети, а не напрямую с клиентской машины, которая может быть заблокирована корпоративным firewall или гео-ограничениями провайдера.
- **Pass-through ключей** — proxy не хранит и не знает API-ключи; клиент передаёт их как обычно (`Authorization`, `x-api-key`, `?key=`), просто через другой домен/адрес.
- **Гео-маршрутизация** — один и тот же Docker-образ можно поднять на серверах в разных странах, и каждый инстанс станет "региональным" через переменные окружения, без изменения кода.

## Аналоги

| Продукт                           | Тип                      | Отличие от ai-proxy                                          |
| :-------------------------------- | :----------------------- | :----------------------------------------------------------- |
| LiteLLM Proxy                     | Open-source, self-hosted | Полноценный enterprise AI gateway: unified OpenAI-формат для 100+ моделей, budgets, virtual keys, cost tracking, guardrails [litellm](https://github.com/BerriAI/litellm). Тяжелее и функциональнее, но сложнее в развёртывании и требует Python-стек. |
| OpenRouter                        | SaaS (облачный)          | Единый API к 300+ моделям от 60+ провайдеров, но ключи и биллинг идут через сам OpenRouter, а не pass-through твоих собственных ключей [dibi8](https://dibi8.com/resources/llm-frameworks/openrouter-unified-llm-api-gateway/). |
| Kong AI Gateway / Bifrost         | Enterprise self-hosted   | Полноценные API-гейтвеи с плагинами, метриками, guardrails — избыточны для личного/командного использования, целятся в корпоративный масштаб [futureagi](https://futureagi.com/blog/best-kong-ai-gateway-alternatives-2026/). |
| Nginx/Envoy как raw reverse proxy | Инфраструктурный уровень | Может делать то же самое (проксирование по пути), но без встроенной auth-модели (allowlist IP, admin-token) и без удобной конфигурации через JSON в env — придётся писать это вручную. |

**Ключевое отличие ai-proxy** — минимализм: один Java-файл-уровень логики, конфигурация целиком через переменные окружения без пересборки образа, никакого хранения ключей, никакой БД, никакого UI. Это осознанный trade-off — простота и контроль ценой отсутствия функций вроде cost tracking, budgets, guardrails, которые есть в LiteLLM.docs.litellm+1

## Пример `PROVIDERS_JSON`

```json
json{
  "gemini": {
    "baseUrl": "https://generativelanguage.googleapis.com"
  },
  "openai": {
    "baseUrl": "https://api.openai.com"
  },
  "anthropic": {
    "baseUrl": "https://api.anthropic.com"
  }
}
```

Каждый ключ верхнего уровня (`gemini`, `openai`, `anthropic`) становится первым сегментом пути в URL твоего proxy: `/gemini/...`, `/openai/...`, `/anthropic/...`.

## Настройка в OpenCode

В OpenCode задаётся только `baseURL` — ключи вводятся штатным способом (`opencode auth login` или `{env:...}`), и OpenCode сам передаёт их в заголовках как обычно, а proxy их просто форвардит без изменений.opencode+1

```
json{
  "$schema": "https://opencode.ai/config.json",
  "provider": {
    "google": {
      "options": { "baseURL": "https://ai-proxy-pog5.onrender.com/gemini/v1beta" }
    },
    "openai": {
      "options": { "baseURL": "https://ai-proxy-pog5.onrender.com/openai/v1" }
    },
    "anthropic": {
      "options": { "baseURL": "https://ai-proxy-pog5.onrender.com/anthropic/v1" }
    }
  }
}
```

## Примеры запросов к AI через proxy

**Gemini:**

```
bashcurl --request POST \
  --url 'https://ai-proxy-pog5.onrender.com/gemini/v1beta/models/gemini-2.5-flash:generateContent?key=YOUR_GEMINI_KEY' \
  --header 'Content-Type: application/json' \
  --data '{"contents":[{"parts":[{"text":"Explain how AI works in a few words"}]}]}'
```

**OpenAI:**

```
bashcurl --request POST \
  --url https://ai-proxy-pog5.onrender.com/openai/v1/chat/completions \
  --header 'Authorization: Bearer YOUR_OPENAI_KEY' \
  --header 'Content-Type: application/json' \
  --data '{"model":"gpt-4o-mini","messages":[{"role":"user","content":"hi"}]}'
```

**Anthropic:**

```
bashcurl --request POST \
  --url https://ai-proxy-pog5.onrender.com/anthropic/v1/messages \
  --header 'x-api-key: YOUR_ANTHROPIC_KEY' \
  --header 'anthropic-version: 2023-06-01' \
  --header 'Content-Type: application/json' \
  --data '{"model":"claude-3-5-sonnet-20241022","max_tokens":100,"messages":[{"role":"user","content":"hi"}]}'
```

## Примеры запросов на изменение настроек на лету

**Разрешить текущий IP на 12 часов (динамический allowlist):**

```
bashcurl --request POST \
  --url https://ai-proxy-pog5.onrender.com/admin/allow-ip \
  --header 'X-Admin-Token: YOUR_ADMIN_TOKEN'
```

**Добавить/изменить провайдеров** — делается не запросом к самому proxy, а через Render Dashboard → Environment → `PROVIDERS_JSON` → Save → Restart. Сам ai-proxy не имеет HTTP-эндпоинта для правки списка провайдеров — эта настройка читается только при старте процесса (см. `ProviderConfigLoader`), поэтому "на лету" здесь означает "без git commit и без пересборки образа", но не "без restart".

## Слетят ли настройки при перезапуске

Здесь два разных типа настроек с разной судьбой:

**Не слетят (переживают restart):**

- `PROVIDERS_JSON`, `ALLOWED_IPS`, `ADMIN_TOKEN` — это переменные окружения, они хранятся в Render Dashboard (или `.env`/docker run на VDI) отдельно от процесса контейнера. При restart/redeploy они читаются заново из того же места.

**Слетят (не переживают restart):**

- Динамически зарегистрированные через `/admin/allow-ip` IP-адреса — они живут **только в оперативной памяти процесса** (`ConcurrentHashMap` в `IpAllowlistService`). Если контейнер перезапускается (например, Render free-план "засыпает" при простое и поднимается заново, либо ты делаешь Manual Deploy), этот список полностью очищается, и тебе нужно зарегистрировать IP повторно через тот же `/admin/allow-ip`.

Это осознанный выбор: динамический список — временный и одноразовый по дизайну, чтобы не плодить персистентное хранилище (БД/файл) там, где хватает TTL в памяти. Если тебе важно, чтобы этот список переживал restart, единственный способ — переносить его в постоянное хранилище (файл на persistent disk или внешняя БД), но это уже усложнение архитектуры, которое стоит делать только если рестарты у тебя частые и это реально мешает.