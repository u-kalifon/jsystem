package jsystem.framework.scripts.jython;

/*
  This is the entry point for all Python use and support. This class is a singleton
  used to hold the Python interpreter (a GraalPy context) which will then be used
  throughout the Scenario run and store state between the different keywords.
*/

import java.nio.file.Files;
import java.nio.file.Path;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.python.embedding.GraalPyResources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PythonInterpreter {
    private static Context context = null;
    private static Logger log = LoggerFactory.getLogger(PythonInterpreter.class);

    private PythonInterpreter() {}

    public static Value exec(String script) {
        if (context == null) {
            // we expect all the python files in the automation project to be found under
            // <project_root>/python
            Path pythonDir = Path.of("python");
            if (!Files.exists(pythonDir)) {
                pythonDir = Path.of("../python"); // if running from the target/ direcotry
            }
            if (!Files.exists(pythonDir)) {
                pythonDir = Path.of("."); // last fallback...
            }

            try {
                context = Context.newBuilder()
                    .apply(GraalPyResources.forExternalDirectory(pythonDir))
                    .allowAllAccess(true)
                    .build();
            } catch (Exception e) {
                log.error("Failed to initialize Python GraalPy context: " + e.getMessage());
                throw e;
            }

            try {
                String graalpyInitScript = """
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
                    """;
                context.eval("python", graalpyInitScript);
            } catch (Exception e) {
                log.error("Failed to initialize the JSystem resources in the GraalPy context: " + e.getMessage());
                throw e;
            }
        }

        // Exec the Python script
        try {
            return context.eval("python", script);
        } catch (Exception e) {
            log.error("Python execution failed: " + e.getMessage());
            throw e;
        }
    }

}
