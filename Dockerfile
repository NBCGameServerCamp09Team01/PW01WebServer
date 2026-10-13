# 웹서버 실행 이미지. 빌드 단계(JDK)에서 jar를 만들고, 실행 단계(JRE)에는 jar만 담는다.
# 만들기: docker build -t pw01/webserver:local --build-arg APP_COMMIT=<커밋 해시> .
# 실행에 필요한 환경 변수는 docs/overview.md 환경변수 표(prod). 비밀값은 이미지에 넣지 않는다.

# ---- 빌드 ----
# 태그는 구체 버전으로 고정한다(같은 Dockerfile이면 같은 이미지)
FROM eclipse-temurin:21.0.12.1_1-jdk-noble AS build
WORKDIR /workspace

# Gradle 파일을 먼저 복사해 의존성 받기를 따로 층으로 둔다. 소스만 바뀌면 이 층은 다시 받지 않는다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# Windows에서 커밋하면 실행 권한이 빠질 수 있다(ci.yml과 같은 이유)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

COPY src src
# 시험은 CI(./gradlew build)에서 돌리므로 여기서는 jar만 만든다
RUN ./gradlew bootJar -x test --no-daemon

# ---- 실행 ----
FROM eclipse-temurin:21.0.12.1_1-jre-noble
WORKDIR /app

# root가 아닌 사용자로 실행한다
RUN groupadd --system app && useradd --system --gid app --no-create-home app

COPY --from=build /workspace/build/libs/pw01-webserver-0.0.1-SNAPSHOT.jar app.jar

# /actuator/info의 app.commit(application.properties info.app.commit)
ARG APP_COMMIT=local
ENV APP_COMMIT=${APP_COMMIT}
# 컨테이너 메모리 한도의 75%까지 힙으로 쓴다
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

USER app
# 8080 게임 API, 8081 관리 포트(prod의 Actuator)
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
