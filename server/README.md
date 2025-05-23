# README

## Запуск сервера

1. Установить python версии 3.10
2. Установить poetry

    ```bash
    python3.10 -m pip install poetry
    ```

3. Находясь в server, установить зависимости

    ```bash
    poetry install
    ```

    или

    ```bash
    make venv
    ```

4. Создать `dev.env` и заполнить его переменными. Пример в `example.env`
5. Экспортировать переменные в текущее окружение

    ```bash
    set -a && . ./dev.env && set +a
    ```

6. Перейти в src и запустить сервер

    ```bash
    cd src && poetry run gunicorn main:app -k uvicorn.workers.UvicornWorker -b $APP_HOST:$APP_PORT
    ```

    или

    ```bash
    make run
    ```

7. Для запуска в фоновом режиме можно добавить --daemon

    ```bash
    cd src && poetry run gunicorn main:app -k uvicorn.workers.UvicornWorker -b $APP_HOST:$APP_PORT --daemon
    ```

    или

    ```bash
    make run_daemon
    ```

