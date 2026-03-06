# Desktop Host

## Как запустить

1. Откройте проект в Android Studio.
2. Выберите конфигурацию desktop-host и запустите main().
3. Сервер будет слушать на 0.0.0.0:8080.

## Как подключить Android TABLE

- В настройках Android TABLE укажите адрес: http://10.0.2.2:8080
- Проверьте:
  - http://localhost:8080/health (на ПК)
  - http://10.0.2.2:8080/health (с Android TABLE)
  - /snapshot, /sync, /input работают по контракту.

