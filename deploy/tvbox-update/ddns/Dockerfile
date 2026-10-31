FROM python:3.12-alpine

WORKDIR /app
COPY ddns.py /app/ddns.py
RUN adduser -D -u 10001 ddns
USER ddns

CMD ["python", "-u", "/app/ddns.py"]
