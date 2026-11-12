"""
Copyright 2005-2010 Ignis Software Tools Ltd. All rights reserved.

This module contains utility functions for use within Jython unit tests written
for Aqua JSystem. They allow logging and access to various jsystem components 
(sut, system objects, etc.).

This updated version of the module uses GraalPy instead of Jython [Udi Kalifon, 2026]
"""

import java

LoggerFactory = java.type("org.slf4j.LoggerFactory")
SystemManagerImpl = java.type("jsystem.framework.system.SystemManagerImpl")
ListenerstManager = java.type("jsystem.framework.report.ListenerstManager")
SutFactory = java.type("jsystem.framework.sut.SutFactory")
MonitorsManager = java.type("jsystem.framework.monitor.MonitorsManager")
RunProperties = java.type("jsystem.framework.RunProperties")
RootFixture = java.type("jsystem.framework.fixture.RootFixture")

log = LoggerFactory.getLogger("PythonInterpreter")
report = ListenerstManager.getInstance()
system = SystemManagerImpl.getInstance()
sut = SutFactory.getInstance().getSutInstance()
monitors = MonitorsManager.getInstance()
runProperties = RunProperties.getInstance()
        
class SystemTestCase:
    pass
