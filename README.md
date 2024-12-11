# Wallet Relying Party Demonstrator

This service is a demonstrator of a wallet relying party, requesting credentials from wallets using [OpenID for Verifiable Presentations](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html).

It builds upon [Signum](https://github.com/a-sit-plus/signum) and [VC-K](https://github.com/a-sit-plus/vck).

This service is intended for demonstration purposes, and makes no guarantees to be correct and/or complete. It should never be used in production systems. All contents subject to change. See [A-SIT Plus Wallet](https://wallet.a-sit.at/) for more information.

This service uses Spring Boot, so one can start the service with  `./gradlew bootRun`. 

When deploying this service (i.e. executing the `jar` produced by `./gradlew bootJar`), `application.yml` may look like this:
```yaml
app:
  public-url: https://example.com/verifier
server:
  port: 8080
  servlet:
    context-path: /verifier
  forward-headers-strategy: framework
```