# syntax=docker/dockerfile:1

FROM golang:1.23.4-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o /out/server ./cmd/server

# Distroless: no shell, no package manager, non-root.
# Configuration is injected at runtime via env vars / mounted .env — never baked in.
FROM gcr.io/distroless/static-debian12:nonroot
WORKDIR /home/nonroot
COPY --from=build /out/server /server
ENV ADDR=:80
EXPOSE 80
USER nonroot:nonroot
ENTRYPOINT ["/server"]
