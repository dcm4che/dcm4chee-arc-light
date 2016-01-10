DCM4CHEE Archive 5.x
====================
Sources: https://github.com/dcm4che/dcm4chee-arc-light
Binaries: https://sourceforge.net/projects/dcm4che/files/dcm4chee-arc-light
Issue Tracker:  https://github.com/dcm4che/dcm4chee-arc-light/issues
Wiki:  https://github.com/dcm4che/dcm4chee-arc-light/wiki

DICOM Archive Java EE application running in WildFly.

This is a complete rewrite of [DCM4CHEE Archive 2.x](http://www.dcm4che.org/confluence/display/ee2/Home).

It started as refactoring of [DCM4CHEE Archive 4.x](https://github.com/dcm4che/dcm4chee-arc-cdi),
eliminating complexity only needed for integration with proprietary systems of the company
which drives development of DCM4CHEE Archive 4.x.

One major improvement to 2.x is the use of LDAP as central configuration,
compliant to the DICOM Application Configuration Management Profile,
specified in [DICOM 2015, Part 15, Annex H][1].

Version 5.0.1 supports DICOM and HL7 Services required for
compliance with IHE Radiology Workflow Integration Profiles:

- [Scheduled Workflow (SWF)][2]
- [Patient Information Reconciliation (PIR)][3]
- [Imaging Object Change Management (IOCM)][4]

for IHE Actor _Image Manager/Archive_.

In long term, 5.x will provide the functionality of 2.x, and there will
be migration tools to upgrade existing installations of 2.x to 5.x.

Build
-----

* Make sure you have [Maven 3](http://maven.apache.org) installed.

* Build `dcm4chee-arc-light` branch of [dcm4che 3 Library](https://github.com/dcm4che/dcm4che) -
  only necessary to build unreleased version, which is not (yet) available in the Maven repository:

        > $git clone https://github.com/dcm4che/dcm4che.git
        > $cd dcm4che
        > $git checkout dcm4chee-arc-light
        > $mvn install

* Build the Archive for a specific database:

        > $mvn install -D db={db2|firebird|h2|mysql|oracle|psql|sqlserver}

    with secured WEB UI:

        > $mvn install -D db={db2|firebird|h2|mysql|oracle|psql|sqlserver} -P secure-ui

    with secured WEB UI and secured RESTful services:

        > $mvn install -D db={db2|firebird|h2|mysql|oracle|psql|sqlserver} -P secure


Installation
------------
* [Installation](https://github.com/dcm4che/dcm4chee-arc-light/wiki/Installation)
* [Running on Docker](https://github.com/dcm4che/dcm4chee-arc-light/wiki/Running-on-Docker)

License
-------
* [Mozilla Public License Version 1.1](http://www.mozilla.org/MPL/1.1/)

[1]: http://dicom.nema.org/medical/dicom/current/output/chtml/part15/chapter_H.html
[2]: http://wiki.ihe.net/index.php?title=Scheduled_Workflow
[3]: http://wiki.ihe.net/index.php?title=Patient_Information_Reconciliation
[4]: http://wiki.ihe.net/index.php?title=Imaging_Object_Change_Management
