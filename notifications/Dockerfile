FROM bellsoft/liberica-runtime-container:jdk-17-glibc AS target
WORKDIR /target
COPY ./target/*.jar /target
RUN mkdir -p /target/dependency && (cd /target/dependency; jar -xf ../*.jar)

FROM bellsoft/liberica-runtime-container:jdk-17-glibc
RUN addgroup -S app_user && adduser -S app_user -G app_user
USER app_user:app_user
COPY --from=target /target/dependency/BOOT-INF/lib /app/lib
COPY --from=target /target/dependency/META-INF /app/META-INF
COPY --from=target /target/dependency/BOOT-INF/classes /app

ENTRYPOINT ["java","-cp","app:app/lib/*","io.averkhoglyad.shortcut.notification.NotificationAppKt"]
