import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/** Source-file launcher used when a runtime contains jdk.compiler but no javac binary. */
public final class JavaTestRunner {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("usage: output-dir main-class source...");
        }
        File output = new File(args[0]);
        if (!output.isDirectory() && !output.mkdirs()) {
            throw new IllegalStateException("cannot create output directory: " + output);
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("jdk.compiler is unavailable");

        List<String> options = new ArrayList<>();
        // Windows installations commonly default javac to windows-1252, while
        // every source in this project is stored as UTF-8. Keep test builds
        // deterministic on every host instead of inheriting the OS code page.
        options.add("-encoding");
        options.add("UTF-8");
        options.add("-d");
        options.add(output.getAbsolutePath());
        for (int i = 2; i < args.length; i++) options.add(args[i]);

        int result = compiler.run(
            null,
            System.out,
            System.err,
            options.toArray(new String[0])
        );
        if (result != 0) throw new IllegalStateException("Java compilation failed: " + result);

        try (URLClassLoader loader = new URLClassLoader(new URL[] { output.toURI().toURL() })) {
            Class<?> test = Class.forName(args[1], true, loader);
            try {
                test.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            } catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof Exception) throw (Exception) cause;
                if (cause instanceof Error) throw (Error) cause;
                throw failure;
            }
        }
    }
}
