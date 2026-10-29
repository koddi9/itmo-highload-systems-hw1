FROM python:3.13.5-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY generate_dashboard.py .
USER nobody
ENTRYPOINT ["python", "generate_dashboard.py"]
