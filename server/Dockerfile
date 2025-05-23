FROM python:3.10-slim
LABEL author=klotzgal

HEALTHCHECK --interval=5m --timeout=3s \
    CMD curl -f http://localhost/health || exit 1

ENV \
	PYTHONDONTWRITEBYTECODE=1 \
	PYTHONUNBUFFERED=1 \
	LANG=en_US.UTF-8 \
    TZ=Europe/Moscow \ 
	POETRY_VIRTUALENVS_PATH=/opt/

WORKDIR /opt
RUN pip install poetry==1.8.4
COPY poetry.lock /opt/poetry.lock
COPY pyproject.toml /opt/pyproject.toml
RUN poetry install

COPY start.sh /opt/start.sh
COPY src /opt/

RUN chown -R 1001 /opt/ \
    && chown -R 1001 /var/cache/ \
    && chown -R 1001 /var/run/ \
    && chmod u-s /usr/bin/gpasswd \
    && chmod u-s /bin/su \
    && chmod u-s /bin/mount \
    && chmod g-s /sbin/unix_chkpwd \
    && chmod u-s /usr/bin/chsh \
    && chmod u-s /usr/bin/chfn \
    && chmod u-s /usr/bin/passwd \
    && chmod g-s /usr/bin/chage \
    && chmod g-s /usr/bin/expiry \
    && chmod u-s /bin/umount \
    && chmod g-s /usr/bin/wall \
    && chmod u-s /usr/bin/newgrp

USER 1001
EXPOSE 8000

ENTRYPOINT ["/bin/bash", "start.sh"]