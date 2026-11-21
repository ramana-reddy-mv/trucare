FROM golang:1.25-alpine AS healthcheck
WORKDIR /src
COPY healthcheck.go .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /healthcheck healthcheck.go

FROM jaegertracing/jaeger:2.23.0
COPY --from=healthcheck /healthcheck /healthcheck