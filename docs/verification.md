# Проверка проекта

Дата: 23 октября 2026, Europe/Chisinau.

Среда: Windows, Microsoft OpenJDK 17.0.19, Maven из IntelliJ IDEA, Docker Engine 29.7.2, Linux containers. PostgreSQL 16.6-alpine и apache/kafka 3.9.1 поднимаются Testcontainers 1.21.4.

| Проверка | Результат |
|---|---|
| mvn verify | BUILD SUCCESS |
| Unit: StatusTest | 25 / 25 |
| Unit: OperationServiceTest | 4 / 4 |
| Integration: LedgerIT | 34 / 34 |
| Пропущенные тесты | 0 |
| docker compose config --quiet | exit 0 |
| Исполняемый Spring Boot JAR | target/operation-ledger-1.0.0.jar |

Полный Maven-прогон занял 1 минуту 10 секунд, включая получение образов и запуск контейнеров. Это результат конкретного окружения, не обещание времени настройки на другой машине.

Текстовые отчёты Surefire/Failsafe сохранены в [test-results](test-results). После собственного запуска полные отчёты будут в target/surefire-reports и target/failsafe-reports.

Первый запуск выявил несовместимость Testcontainers 1.21.3 с Docker 29. Версия обновлена до 1.21.4; после исправления весь набор прошёл. Изменение соответствует [релизу Testcontainers 1.21.4](https://github.com/testcontainers/testcontainers-java/releases/tag/1.21.4).

Нагрузочный сценарий k6, p95, время подготовки окружения «15 минут» и Jenkins pipeline здесь не выполнялись. Dockerfile не проходил отдельный docker build; проверены Maven-сборка приложения, интеграции на контейнерах и валидность Compose. История коммерческих review или работы на production этим проектом не подтверждается.
