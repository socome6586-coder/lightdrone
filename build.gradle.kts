import java.util.zip.ZipFile

plugins {
    java
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.lightdrone"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile> {
    options.release.set(21)
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    // Thymeleaf Security
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")

    // OAuth2 소셜 로그인 (Google, Kakao)
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // PostgreSQL (서버 배포용)
    runtimeOnly("org.postgresql:postgresql")

    // Flyway DB 마이그레이션 (PostgreSQL)
    //  - 버전은 Spring Boot BOM(3.4.5)이 관리한다.
    //  - Flyway 10+ 는 PostgreSQL 지원을 별도 모듈(flyway-database-postgresql)로 분리한다.
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Oracle (로컬 개발용)
    runtimeOnly("com.oracle.database.jdbc:ojdbc11")
    runtimeOnly("com.oracle.database.jdbc:ucp11")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Coolsms (SMS 발송)
    implementation("net.nurigo:sdk:4.3.2")

    // jsoup (사용자 입력 HTML 새니타이징 - 저장형 XSS 방지)
    implementation("org.jsoup:jsoup:1.17.2")

    // AWS SDK v2 (S3 이미지 스토리지 - storage.type=s3 설정 시 사용)
    implementation(platform("software.amazon.awssdk:bom:2.26.25"))
    implementation("software.amazon.awssdk:s3")

    // Dev Tools
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // 통합 테스트용 인메모리 DB (운영/로컬 DB 와 완전히 격리 — test 프로파일에서만 사용)
    testRuntimeOnly("com.h2database:h2")
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED"
    )
}

// 콘솔 UTF-8 인코딩 설정
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

// ── 배포 JAR 에서 로컬 비밀값 파일 제외 ───────────────────────────────
//  application-local.yml(실제 로컬 비밀값)과 .example 템플릿은 배포 산출물(bootJar)에
//  포함되면 안 된다. bootRun(로컬 실행)은 build/resources/main 을 직접 읽으므로 영향 없음.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    exclude("application-local.yml", "application-local.yml.example")
}

// ── 배포 JAR 비밀값 누출 검증 ─────────────────────────────────────────
//  빌드 산출물(bootJar) 안에
//   1) application-local.yml / .example 이 들어있지 않은지,
//   2) 번들된 application.yml 에 평문 비밀값(예: 토스 테스트키 prefix, 과거 노출 키 등)이
//      남아있지 않은지 검사한다. 하나라도 걸리면 빌드를 실패시켜 사고를 조기에 차단한다.
tasks.register("verifyNoSecretsInJar") {
    group = "verification"
    description = "bootJar 안에 비밀값/로컬 설정 파일이 포함되지 않았는지 검증"
    dependsOn("bootJar")

    doLast {
        val jarFile = tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar")
            .get().archiveFile.get().asFile
        require(jarFile.exists()) { "bootJar 산출물을 찾을 수 없음: ${jarFile.absolutePath}" }

        // JAR 안에 절대 있으면 안 되는 엔트리(로컬 비밀값/템플릿)
        val forbiddenEntries = listOf(
            "BOOT-INF/classes/application-local.yml",
            "BOOT-INF/classes/application-local.yml.example"
        )
        // 번들 application.yml 에 남아있으면 안 되는 비밀값 패턴
        //  - 토스 라이브/테스트 시크릿키 prefix 등 (필요 시 자체 키 패턴 추가)
        val secretPatterns = listOf(
            "live_sk_", "test_sk_"             // 토스 시크릿키
        )

        val violations = mutableListOf<String>()
        ZipFile(jarFile).use { zip ->
            for (entry in forbiddenEntries) {
                if (zip.getEntry(entry) != null) {
                    violations += "금지된 파일이 JAR 에 포함됨: $entry"
                }
            }
            val appYml = zip.getEntry("BOOT-INF/classes/application.yml")
            if (appYml != null) {
                val text = zip.getInputStream(appYml).bufferedReader(Charsets.UTF_8).readText()
                for (pat in secretPatterns) {
                    if (text.contains(pat)) {
                        violations += "application.yml 에 비밀값 패턴 발견: \"$pat\""
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "비밀값 누출 검증 실패 (${jarFile.name}):\n  - " + violations.joinToString("\n  - ")
            )
        }
        logger.lifecycle("verifyNoSecretsInJar: OK — ${jarFile.name} 에 비밀값/로컬 설정 파일 없음")
    }
}

// check(=빌드 검증 단계)에 비밀값 검증을 포함시켜, 일반 빌드에서도 자동으로 검사되게 한다.
tasks.named("check") {
    dependsOn("verifyNoSecretsInJar")
}
