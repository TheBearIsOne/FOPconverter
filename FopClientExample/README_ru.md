# FopClientExample

Консольный .NET 8 клиент для Java-сервиса FOP Converter.

## Возможности

- sync конвертация через `/api/v1/convert/sync`
- async submit + polling + download через `/api/v1/convert` с использованием `statusUrl` и `downloadUrl` из API-ответов
- `IHttpClientFactory`, `async/await`, retry на transient HTTP errors
- `CancellationToken` во всей цепочке вызовов
- Serilog structured logging

## Запуск

```powershell
dotnet restore FopClientExample/FopClientExample.sln
dotnet run --project FopClientExample/src/FopClientExample/FopClientExample.csproj
```

## Конфигурация

Все параметры находятся в `src/FopClientExample/appsettings.json`:

- `ConversionApi:BaseUrl` — URL Java-сервиса
- `ConversionApi:RequestTimeoutSeconds` — таймаут одного HTTP запроса
- `ConversionApi:PollingIntervalSeconds` — шаг polling статуса
- `ConversionApi:PollingTimeoutSeconds` — общий timeout polling
- `ConversionApi:MaxRetries` — число retry на сетевые ошибки
- `Samples:*` — пути к demo-файлам и output directory

## Demo flow

1. Клиент читает `sample.fo` и вызывает sync endpoint.
2. Сохраняет результат в output directory.
3. Читает `sample.xml` + `sample.xslt` и вызывает async endpoint через JSON base64.
4. Поллит job status по `statusUrl` из `202 Accepted` ответа до `completed`.
5. Скачивает итоговый artifact по `downloadUrl` из job status ответа и сохраняет его в output directory.

## Additional FO Samples

В [FopClientExample/samples](../FopClientExample/samples) добавлены дополнительные XSL-FO документы для ручного тестирования:

- `sample-font-showcase.fo` — латиница и Base 14 fonts.
- `sample-cyrillic.fo` — русский, украинский, болгарский.
- `sample-cjk.fo` — Chinese / Japanese / Korean.
- `sample-rtl.fo` — Arabic / Hebrew, right-to-left blocks.

Текущий demo-клиент по-прежнему использует `sample.fo`, но любой из этих файлов можно подставить в ручной `curl.exe` запрос или в собственный тестовый сценарий.

В compose-окружении backend image теперь уже содержит `DejaVu`, `Liberation` и `Noto` font packages, поэтому кириллица и CJK samples рендерятся заметно стабильнее, чем при Base 14 fallback. Для локального запуска вне Docker результат зависит от того, какие шрифты видит локальная JVM.
