# Start with the existing image
FROM dcm4che/dcm4chee-arc-psql:5.33.1-secure-ui

# Remove existing WAR and EAR files
RUN rm -rf /opt/wildfly/standalone/deployments/*

# Copy your WAR and EAR files into the deployments directory
COPY dcm4chee-arc-ear/target/dcm4chee-arc-ear-5.33.1-psql.ear /opt/wildfly/standalone/deployments/
COPY dcm4chee-arc-ui2/target/dcm4chee-arc-ui2-5.33.1-secure.war /opt/wildfly/standalone/deployments/
