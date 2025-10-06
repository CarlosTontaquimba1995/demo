#!/bin/bash

echo "🚀 Starting Kafka infrastructure with Docker Compose..."
docker-compose up -d

echo "\n🔄 Waiting for Kafka to be ready..."
sleep 10

echo "\n📋 Kafka is running!"
echo "Kafka UI: http://localhost:8080"
echo "Kafdrop: http://localhost:9000"
echo "\nTo stop the services, run: docker-compose down"
