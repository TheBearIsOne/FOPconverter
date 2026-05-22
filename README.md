# FOP Converter

Java 21 / Spring Boot 3 сервис асинхронной конвертации документов на базе Apache FOP и пример клиента на .NET 8.

## Структура

- `converter-service` — backend сервис.
- `docker-compose.yml` — локальный стек с Redis, NATS JetStream, Prometheus, Grafana, Elasticsearch и Kibana.
- `openapi/fop-converter-openapi.yaml` — зафиксированный API-контракт.
- `FopClientExample` — консольный .NET 8 клиент.

## XSL-FO Samples

В [FopClientExample/samples](c:\AI%20Soft\JAVA\FOPConverter\FopClientExample\samples) теперь доступны дополнительные `.fo`-примеры:

- `sample.fo` — минимальный sync demo, который использует текущий базовый сценарий клиента.
- `sample-font-showcase.fo` — безопасный showcase Base 14 fonts (`Helvetica`, `Times`, `Courier`) для латиницы и табличного текста. Этот пример проверен в текущем compose-стеке.
- `sample-cyrillic.fo` — пример для кириллицы с семействами `DejaVu Sans`, `DejaVu Serif`, `Liberation Sans`, `Arial Unicode MS`. Проверен в compose после включения font auto-detect.
- `sample-cjk.fo` — пример для китайского, японского и корейского текста с семействами `Noto Sans/Serif CJK` и `Source Han`. Проверен в compose после установки Noto fonts.
- `sample-rtl.fo` — пример для Arabic/Hebrew с `direction="rtl"` и семействами `Noto Naskh Arabic`, `Noto Sans Hebrew`, `Amiri`. Рендерится в compose, но Apache FOP может логировать предупреждения про complex scripts.

Текущее состояние font support:

- Docker runtime теперь устанавливает `DejaVu`, `Liberation` и `Noto` font packages.
- [converter-service/src/main/resources/fop/fop.xconf](c:\AI%20Soft\JAVA\FOPConverter\converter-service\src\main\resources\fop\fop.xconf) включает `auto-detect` для PDF renderer.
- [fonts/README.md](c:\AI%20Soft\JAVA\FOPConverter\fonts\README.md) описывает repo-local директорию `./fonts` для предсказуемого локального запуска вне Docker.
- Для локального запуска вне Docker Java runtime должен видеть те же шрифты на машине, либо нужные `.ttf/.otf/.ttc` можно положить в `./fonts` и запускать сервис из корня репозитория.

## Локальный запуск

```powershell
mvn -q -pl converter-service -am package -DskipTests
docker compose up -d --build
```

После старта сервисы доступны по адресам:

- API / Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Health: `http://localhost:8080/actuator/health`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Kibana: `http://localhost:5601`
- Elasticsearch: `http://localhost:9200`

Многоязычные PDF в compose-режиме теперь опираются на установленные внутри контейнера семейства `DejaVu`, `Liberation`, `Noto Sans CJK`, `Noto Serif CJK`, `Noto Naskh Arabic`, `Noto Sans Hebrew`.

Если нужно пересобрать только backend image и перезапустить backend контейнеры:

```powershell
docker compose build app
docker compose up -d app worker
```

Полная автоматическая проверка стека одной командой:

```powershell
.\smoke-test.ps1
```

Проверка multilingual font samples:

```powershell
.\font-smoke-test.ps1
```

## Профили

- `default` — базовая конфигурация.
- `local-dev` — локальный запуск через `docker compose`.
- `async-worker` — role profile для воркера JetStream.

## Примеры curl

На Windows для multipart-запросов используйте `curl.exe`, а не alias PowerShell `curl`.

Синхронная конвертация XSL-FO в PDF:

```powershell
curl.exe -X POST http://localhost:8080/api/v1/convert/sync `
  -F payloadType=XSL_FO `
  -F outputFormat=PDF `
  -F primaryDocument=@FopClientExample/samples/sample.fo `
  --output output.pdf
```

Чтобы прогнать другой sample-файл, достаточно заменить путь в `primaryDocument`, например:

```powershell
curl.exe -X POST http://localhost:8080/api/v1/convert/sync `
  -F payloadType=XSL_FO `
  -F outputFormat=PDF `
  -F primaryDocument=@FopClientExample/samples/sample-cyrillic.fo `
  --output output-cyrillic.pdf
```

Пример для CJK:

```powershell
curl.exe -X POST http://localhost:8080/api/v1/convert/sync `
  -F payloadType=XSL_FO `
  -F outputFormat=PDF `
  -F primaryDocument=@FopClientExample/samples/sample-cjk.fo `
  --output output-cjk.pdf
```

Пример для RTL:

```powershell
curl.exe -X POST http://localhost:8080/api/v1/convert/sync `
  -F payloadType=XSL_FO `
  -F outputFormat=PDF `
  -F primaryDocument=@FopClientExample/samples/sample-rtl.fo `
  --output output-rtl.pdf
```

Асинхронная конвертация XML + XSLT в PDF через JSON base64:

```powershell
$xml = [Convert]::ToBase64String([IO.File]::ReadAllBytes("FopClientExample/samples/sample.xml"))
$xslt = [Convert]::ToBase64String([IO.File]::ReadAllBytes("FopClientExample/samples/sample.xslt"))
$body = @{
  payloadType = "XML_XSLT"
  outputFormat = "PDF"
  primaryDocumentBase64 = $xml
  primaryFileName = "sample.xml"
  secondaryDocumentBase64 = $xslt
  secondaryFileName = "sample.xslt"
} | ConvertTo-Json

curl.exe -X POST http://localhost:8080/api/v1/convert -H "Content-Type: application/json" -d $body
```

Проверка статуса:

```powershell
curl.exe http://localhost:8080/api/v1/jobs/{jobId}
```

Скачивание результата:

```powershell
curl.exe http://localhost:8080/api/v1/jobs/{jobId}/download --output converted.pdf
```

Проверка health:

```powershell
curl.exe http://localhost:8080/actuator/health
curl.exe http://localhost:8080/actuator/health/readiness
curl.exe http://localhost:8080/actuator/prometheus
```

Полный async smoke-test через `curl.exe`:

```powershell
$xml = [Convert]::ToBase64String([IO.File]::ReadAllBytes("FopClientExample/samples/sample.xml"))
$xslt = [Convert]::ToBase64String([IO.File]::ReadAllBytes("FopClientExample/samples/sample.xslt"))
$body = @{
  payloadType = "XML_XSLT"
  outputFormat = "PDF"
  primaryDocumentBase64 = $xml
  primaryFileName = "sample.xml"
  secondaryDocumentBase64 = $xslt
  secondaryFileName = "sample.xslt"
} | ConvertTo-Json

$response = curl.exe -sS -X POST http://localhost:8080/api/v1/convert -H "Content-Type: application/json" -d $body
$jobId = ($response | ConvertFrom-Json).jobId
$status = $null
for ($i = 0; $i -lt 60; $i++) {
  $status = curl.exe -sS http://localhost:8080/api/v1/jobs/$jobId | ConvertFrom-Json
  if ($status.status -eq "completed") { break }
  Start-Sleep -Milliseconds 1000
}

$status | ConvertTo-Json
curl.exe http://localhost:8080/api/v1/jobs/$jobId/download --output async-output.pdf
```

Если нужен multipart async submit вместо JSON:

```powershell
curl.exe -X POST http://localhost:8080/api/v1/convert `
  -F payloadType=XML_XSLT `
  -F outputFormat=PDF `
  -F primaryDocument=@FopClientExample/samples/sample.xml `
  -F secondaryDocument=@FopClientExample/samples/sample.xslt
```

## Проверка решения

Быстрая ручная проверка после `docker compose up -d --build`:

1. Убедиться, что контейнеры подняты: `docker compose ps`
2. Проверить API: `curl.exe http://localhost:8080/actuator/health`
3. Проверить sync-конвертацию: выполнить запрос на `/api/v1/convert/sync`
4. Проверить async-конвертацию: submit на `/api/v1/convert`, затем `GET /api/v1/jobs/{jobId}` и `GET /api/v1/jobs/{jobId}/download`
5. Открыть Swagger UI: `http://localhost:8080/swagger-ui/index.html`

Во время smoke-test в этой среде были успешно получены файлы:

- `tmp-test-output/smoke-sync.pdf`
- `tmp-test-output/smoke-async.pdf`

Для автоматической проверки используйте скрипт `smoke-test.ps1`. Он:

1. Проверяет `/actuator/health`
2. Выполняет sync-конвертацию `sample.fo -> PDF`
3. Выполняет async-конвертацию `sample.xml + sample.xslt -> PDF`
4. Скачивает оба результата в `tmp-test-output`
5. Проверяет наличие индекса `fopconverter-logs-*` в Elasticsearch
6. Проверяет наличие поля `service_role` в свежих логах Elasticsearch
7. Проверяет, что Kibana Data View `fopconverter-logs` создан автоматически

## Observability

- Метрики: `/actuator/prometheus`
- Health: `/actuator/health`, `/actuator/health/readiness`, `/actuator/health/liveness`
- JSON logs: `converter-service/src/main/resources/logback-spring.xml`
- Grafana dashboard: `observability/grafana/dashboards/fop-converter-dashboard.json`
- OTLP endpoint по умолчанию: `http://otel-collector:4318`

### Kibana и логи

Kibana открывается по адресу `http://localhost:5601`.

Текущее состояние observability-конфига:

- Elasticsearch и Kibana подняты и доступны через `docker compose`
- приложение пишет JSON-логи в stdout контейнера и в файловые логи внутри `artifacts/logs`
- `logstash` забирает эти JSON-файлы и индексирует их в Elasticsearch в индекс `fopconverter-logs-*`
- в каждом документе лога есть поле `service_role` со значением `app` или `worker`
- `observability-bootstrap` автоматически создает index template `fopconverter-logs` и Kibana Data View `fopconverter-logs`
- `otel-collector` по-прежнему принимает OTLP и в MVP экспортирует traces в `debug`

Примечание: `observability-bootstrap` — одноразовый сервис и после успешной инициализации может завершаться со статусом `0`. Это штатное поведение.

Как посмотреть логи сейчас:

```powershell
docker logs fopconverter-app-1 --tail 200
docker logs fopconverter-worker-1 --tail 200
docker logs fopconverter-logstash-1 --tail 200
docker logs fopconverter-elasticsearch-1 --tail 200
docker logs fopconverter-kibana-1 --tail 200
```

Как открыть Kibana:

1. Перейти в браузере на `http://localhost:5601`
2. Дождаться загрузки стартового экрана Kibana
3. Перейти в `Stack Management -> Data Views`
4. Убедиться, что уже существует Data View `fopconverter-logs`
5. Перейти в `Discover`
6. Добавить фильтр по полю `service_role`, если нужно разделить `app` и `worker`
7. Для проверки Elasticsearch можно выполнить запрос в `Dev Tools`:

```json
GET /
```

Пример запроса логов в Dev Tools:

```json
GET fopconverter-logs-*/_search
{
  "size": 20,
  "sort": [
    {
      "@timestamp": "desc"
    }
  ]
}
```

Пример только для worker-логов:

```json
GET fopconverter-logs-*/_search
{
  "query": {
    "term": {
      "service_role": "worker"
    }
  },
  "sort": [
    {
      "@timestamp": "desc"
    }
  ]
}
```

## .NET клиент

```powershell
dotnet run --project FopClientExample/src/FopClientExample/FopClientExample.csproj
```

Клиент читает sample FO/XML/XSLT файлы из `FopClientExample/samples`, выполняет sync и async сценарии и сохраняет результаты в `FopClientExample/output`.