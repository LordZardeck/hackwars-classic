FROM --platform=linux/amd64 mysql:5.7

RUN yum install -y unzip \
    && yum clean all \
    && rm -rf /var/cache/yum

COPY src/main/resources/db/db.zip /seed/db.zip
COPY docker/mysql/init/00-import-seed.sh /docker-entrypoint-initdb.d/00-import-seed.sh

RUN chmod +x /docker-entrypoint-initdb.d/00-import-seed.sh
