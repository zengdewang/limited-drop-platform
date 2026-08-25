# 单仓库 Maven 多模块

All six services plus a shared `common` module (events / DTOs / config) live in **one Maven multi-module repository** (parent pom), with a single `docker-compose.yml` for the middleware. One Maven command builds the whole stack; the repo is itself the demo artifact.

Considered: one repo per service. Rejected — solo project; a single artifact is easier to build, run, and link on the resume.
