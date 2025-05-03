# Start with the existing image
FROM dcm4che/dcm4chee-arc-psql:5.33.1-secure-ui

COPY dcm4chee-arc-ear/target/dcm4chee-arc-ear-5.33.1-psql.ear /docker-entrypoint.d/deployments/
COPY dcm4chee-arc-ui2/target/dcm4chee-arc-ui2-5.33.1-secure.war /docker-entrypoint.d/deployments/
