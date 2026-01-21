# 1. SAHNE: Custom JRE Oluşturma (jre-build)
FROM mirror.gcr.io/library/eclipse-temurin:21-jdk-alpine AS jre-build

# Gerekli minimal Java modüllerini seçerek saldırı yüzeyini daraltıyoruz
RUN $JAVA_HOME/bin/jlink \
         --add-modules java.base,java.logging,java.xml,jdk.unsupported,java.sql,java.naming,java.desktop,java.management,java.security.jgss,java.instrument \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output /javaruntime

# 2. SAHNE: Final İmaj (Runtime)
# Ubuntu yerine distroless veya minimal Alpine/Wolfi tercih edilebilir, 
# ancak uyumluluk için Ubuntu 24.04'te kalarak sertleştirme yapıyoruz.
FROM mirror.gcr.io/library/ubuntu:24.04

LABEL maintainer="Beyaz Şapkalı Hacker & Mimari Ekibi"

# Çevresel değişkenler
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:/opt/teku/bin:${PATH}"
ENV LANG=C.UTF-8

# JRE'yi kopyala
COPY --from=jre-build /javaruntime $JAVA_HOME

# Sistem paketlerini güncelle ve sadece kritik bağımlılıkları kur
# adduser ve curl gibi araçlar yüklendikten sonra temizlik yapılır
RUN apt-get update && apt-get upgrade -y && \
    apt-get install -y --no-install-recommends curl libc6 && \
    # Ubuntu 23.10+ varsayılan 'ubuntu' kullanıcısını sil ve 'teku' oluştur (Hata payı sıfır)
    userdel -r ubuntu 2>/dev/null || true && \
    groupadd -g 1000 teku && \
    useradd -u 1000 -g teku -s /bin/false -m -d /opt/teku teku && \
    # Gereksiz dosyaları temizle
    apt-get purge -y adduser && \
    apt-get autoremove -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Uygulama dizini hazırlığı
WORKDIR /opt/teku

# Teku binary'lerini kopyala (Sadece gerekli olanları)
COPY --chown=teku:teku teku /opt/teku/

# Güvenlik: Yazma izinlerini sadece gerekli dizinlerle sınırla
RUN chmod 0755 /opt/teku/bin/teku



# Kullanıcıya geçiş
USER teku

# Teku API ve P2P Konfigürasyonları
ENV TEKU_REST_API_INTERFACE="0.0.0.0" \
    TEKU_VALIDATOR_API_INTERFACE="0.0.0.0" \
    TEKU_METRICS_INTERFACE="0.0.0.0"

# Portlar: Metrics, REST, P2P (TCP/UDP)
EXPOSE 8008 5051 9000 9000/udp

# Sağlık kontrolü (Healthcheck) - İmaj güvenliğini artırır
HEALTHCHECK --interval=60s --timeout=10s --retries=3 \
  CMD curl -f http://localhost:5051/eth/v1/node/health || exit 1

ENTRYPOINT ["teku"]

# Metadata
ARG BUILD_DATE
ARG VCS_REF
ARG VERSION
LABEL org.opencontainers.image.title="Teku" \
      org.opencontainers.image.description="Ethereum Consensus Client - Hardened Image" \
      org.opencontainers.image.version=$VERSION \
      org.opencontainers.image.created=$BUILD_DATE \
      org.opencontainers.image.licenses="Apache 2.0"
