.PHONY: format check venv clean fclean run run_daemon
PYTHON=/usr/bin/python3.10
VENV=.venv
PYEXEC=poetry run

all: run

format:
	$(PYEXEC) ruff format .

check:
ifneq ("", "$(wildcard .flake8)")
	$(PYEXEC) flake8 .
else
	$(PYEXEC) ruff check
endif

venv:
	poetry env use $(PYTHON)
	poetry install

clean:
	rm -r htmlcov .coverage __pycache__ .pytest_cache .ruff_cache

fclean: clean
	rm -r *.log *.log.[0-9]*

run:
	cd src && $(PYEXEC) gunicorn main:app -k uvicorn.workers.UvicornWorker -b ${APP_HOST}:${APP_PORT}

run_daemon:
	cd src && $(PYEXEC) gunicorn main:app -k uvicorn.workers.UvicornWorker -b ${APP_HOST}:${APP_PORT} --daemon
