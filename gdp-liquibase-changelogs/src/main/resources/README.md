Liquibase Layout
================

The main changeLog.xml file references in the correct order changeLogs from each
phase of the changes.  The first phase is creating tables, so createTables includes
all tables that need to be created.  This is all we are doing now, but any additional
tables should be added here and other changes should happen elsewhere.

Additional folders should be added for schema changes and broken up based on things
such as areas of the application or version changes.  Table relations will be another
problem, and it will likely make sense to set up another directory to handle those.