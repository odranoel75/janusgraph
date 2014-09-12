package com.thinkaurelius.titan.hadoop.formats.util;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.thinkaurelius.titan.core.TitanEdge;
import com.thinkaurelius.titan.core.TitanGraph;
import com.thinkaurelius.titan.core.TitanProperty;
import com.thinkaurelius.titan.core.TitanVertex;
import com.thinkaurelius.titan.hadoop.FaunusEdge;
import com.thinkaurelius.titan.hadoop.FaunusProperty;
import com.thinkaurelius.titan.hadoop.FaunusVertex;
import com.thinkaurelius.titan.util.system.IOUtils;
import com.tinkerpop.gremlin.groovy.jsr223.DefaultImportCustomizerProvider;
import com.tinkerpop.gremlin.groovy.jsr223.GremlinGroovyScriptEngine;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.*;

import static com.thinkaurelius.titan.hadoop.compat.HadoopCompatLoader.DEFAULT_COMPAT;

/**
 * Encapsulates a user-provided Gremlin-Groovy incremental loading script.
 * Checks which methods the script provides (if any), compiles each method,
 * and prepares context variable bindings when executing the compiled
 * method(s).
 */
public class LoaderScriptWrapper {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(LoaderScriptWrapper.class);

    private static final DefaultImportCustomizerProvider importCustomizer =
            new DefaultImportCustomizerProvider(
                    ImmutableSet.of( /* nonstatic */
                            FaunusVertex.class.getCanonicalName(),
                            FaunusEdge.class.getCanonicalName(),
                            TitanGraph.class.getCanonicalName(),
                            Mapper.Context.class.getCanonicalName(),
                            FaunusProperty.class.getCanonicalName(),
                            TitanVertex.class.getCanonicalName(),
                            TitanEdge.class.getCanonicalName()),
                    ImmutableSet.<String>of() /* static */);

    public enum Counters {
        VERTEX_LOADER_SCRIPT_CALLS,
        VERTEX_LOADER_SCRIPT_EXCEPTIONS,
        VERTEX_LOADER_SCRIPT_RETURNS,
        EDGE_LOADER_SCRIPT_CALLS,
        EDGE_LOADER_SCRIPT_EXCEPTIONS,
        EDGE_LOADER_SCRIPT_RETURNS,
        PROP_LOADER_SCRIPT_CALLS,
        PROP_LOADER_SCRIPT_EXCEPTIONS,
        PROP_LOADER_SCRIPT_RETURNS,
    }

    private final GremlinGroovyScriptEngine loaderEngine;
    private final CompiledScript vertexMethod;
    private final CompiledScript propMethod;
    private final CompiledScript edgeMethod;

    private static final ImmutableMap<String, Class<?>> vertexArguments = ImmutableMap.of(
            "faunusVertex", FaunusVertex.class,
            "graph", TitanGraph.class,
            "context", Mapper.Context.class,
            "log", Logger.class
    );

    private static final ImmutableMap<String, Class<?>> propArguments = ImmutableMap.of(
            "faunusProperty", TitanProperty.class,
            "vertex", TitanVertex.class,
            "graph", TitanGraph.class,
            "context", Mapper.Context.class,
            "log", Logger.class
    );

    private static final ImmutableMap<String, Class<?>> edgeArguments;

    static {
        ImmutableMap.Builder<String, Class<?>> b = ImmutableMap.builder();
        b.put("faunusEdge", FaunusEdge.class);
        b.put("inVertex", TitanVertex.class);
        b.put("outVertex", TitanVertex.class);
        b.put("graph", TitanGraph.class);
        b.put("context", Mapper.Context.class);
        b.put("log", Logger.class);
        edgeArguments = b.build();
    }

    public LoaderScriptWrapper(FileSystem fs, Path scriptPath) throws IOException {
        this(getScriptString(fs, scriptPath));
    }

    public LoaderScriptWrapper(String scriptString) throws IOException {
        loaderEngine = new GremlinGroovyScriptEngine(importCustomizer);
        vertexMethod = getVertexMethod(scriptString, loaderEngine);
        propMethod = getPropMethod(scriptString, loaderEngine);
        edgeMethod = getEdgeMethod(scriptString, loaderEngine);
    }

    public boolean hasVertexMethod() {
        return null != vertexMethod;
    }

    public boolean hasPropMethod() {
        return null != propMethod;
    }

    public boolean hasEdgeMethod() {
        return null != edgeMethod;
    }

    public TitanVertex getVertex(FaunusVertex faunusVertex, TitanGraph graph, Mapper.Context context) {
        Bindings bindings = new SimpleBindings();
        bindings.put("faunusVertex", faunusVertex);
        bindings.put("graph", graph);
        bindings.put("context", context);
        bindings.put("log", LOGGER);
        DEFAULT_COMPAT.incrementContextCounter(context, Counters.VERTEX_LOADER_SCRIPT_CALLS, 1L);
        try {
            TitanVertex tv = (TitanVertex)vertexMethod.eval(bindings);
            LOGGER.debug("Compiled vertex loader script returned {}", tv);
            DEFAULT_COMPAT.incrementContextCounter(context, Counters.VERTEX_LOADER_SCRIPT_RETURNS, 1L);
            return tv;
        } catch (ScriptException e) {
            DEFAULT_COMPAT.incrementContextCounter(context, Counters.VERTEX_LOADER_SCRIPT_EXCEPTIONS, 1L);
            throw new RuntimeException(e);
        }
    }

    public void getProp(TitanProperty faunusProperty, TitanVertex vertex, TitanGraph graph, Mapper.Context context) {
        Bindings bindings = new SimpleBindings();
        bindings.put("faunusProperty", faunusProperty);
        bindings.put("vertex", vertex);
        bindings.put("graph", graph);
        bindings.put("context", context);
        bindings.put("log", LOGGER);
        DEFAULT_COMPAT.incrementContextCounter(context, Counters.PROP_LOADER_SCRIPT_CALLS, 1L);
        try {
            propMethod.eval(bindings);
            LOGGER.debug("Compiled property loader method invoked");
            DEFAULT_COMPAT.incrementContextCounter(context, Counters.PROP_LOADER_SCRIPT_RETURNS, 1L);
        } catch (ScriptException e) {
            DEFAULT_COMPAT.incrementContextCounter(context, Counters.PROP_LOADER_SCRIPT_EXCEPTIONS, 1L);
            throw new RuntimeException(e);
        }
    }

    public TitanEdge getEdge(FaunusEdge faunusEdge, TitanVertex in, TitanVertex out, TitanGraph graph, Mapper.Context context) {
        Bindings bindings = new SimpleBindings();
        bindings.put("faunusEdge", faunusEdge);
        bindings.put("inVertex", in);
        bindings.put("outVertex", out);
        bindings.put("graph", graph);
        bindings.put("context", context);
        bindings.put("log", LOGGER);
        DEFAULT_COMPAT.incrementContextCounter(context, Counters.EDGE_LOADER_SCRIPT_CALLS, 1L);
        try {
            TitanEdge edge = (TitanEdge)edgeMethod.eval(bindings);
            LOGGER.debug("Compiled edge method returned {}", edge);
            DEFAULT_COMPAT.incrementContextCounter(context, Counters.EDGE_LOADER_SCRIPT_RETURNS, 1L);
            return edge;
        } catch (ScriptException e) {
            DEFAULT_COMPAT.incrementContextCounter(context, Counters.EDGE_LOADER_SCRIPT_EXCEPTIONS, 1L);
            throw new RuntimeException(e);
        }
    }

    private static String getScriptString(FileSystem fs, Path scriptPath) throws IOException {
        // Read the Path argument off the FileSystem argument into a string
        InputStreamReader isr = null;
        try {
            isr = new InputStreamReader(fs.open(scriptPath));
            StringWriter wr = new StringWriter();
            org.apache.commons.io.IOUtils.copy(isr, wr);
            return wr.toString();
        } finally {
            IOUtils.closeQuietly(isr);
        }
    }

    private static CompiledScript getVertexMethod(String script, GremlinGroovyScriptEngine loaderEngine) {
        return getMethod(script, loaderEngine, "getOrCreateVertex", vertexArguments);

    }

    private static CompiledScript getPropMethod(String script, GremlinGroovyScriptEngine loaderEngine) {
        return getMethod(script, loaderEngine, "getOrCreateVertexProperty", propArguments);

    }

    private static CompiledScript getEdgeMethod(String script, GremlinGroovyScriptEngine loaderEngine) {
        return getMethod(script, loaderEngine, "getOrCreateEdge", edgeArguments);
    }

    private static CompiledScript getMethod(String script, GremlinGroovyScriptEngine loaderEngine, String methodName, Map<String, Class<?>> args) {
        CompiledScript compiled = null;

        // metaString will contain the user's script, a newline, and then check whether
        // the method described by methodName and args is actually defined in the user's script
        StringBuilder metaString = new StringBuilder();
        metaString.append(script);

        // callString will contain the user's script, a newline, and then a call
        // to the method described by methodName and args
        StringBuilder callString = new StringBuilder();
        callString.append(metaString.toString());

        String argTypeString = Joiner.on(",").join(Iterables.transform(args.values(), new Function<Class<?>, String>() {
            @Override
            public String apply(Class<?> input) {
                return input.getCanonicalName() + ".class";
            }
        }));

        metaString.append("\n");
        metaString.append(String.format("metaClass.getMetaMethod('%s', %s) != null", methodName, argTypeString));

        String argNameString = Joiner.on(",").join(args.keySet());
        String invocation = String.format("%s(%s)", methodName, argNameString);
        callString.append("\n");
        callString.append(invocation);

        try {
            LOGGER.debug("Check script:\n{}", metaString.toString());
            LOGGER.debug("Loader script:\n{}", callString.toString());
            CompiledScript checkScript = loaderEngine.compile(metaString.toString());
            // Check whether the method was defined
            Boolean s = (Boolean)checkScript.eval();
            if (null != s && s) {
                // It is defined: compile a script that calls the method
                compiled = loaderEngine.compile(callString.toString());
            }
            LOGGER.debug("Tested whether script contained method {}: {}/{}", invocation, s, compiled);
        } catch (RuntimeException e) {
            LOGGER.debug("Custom loader script does not define {}", invocation, e);
        } catch (ScriptException e) {
            LOGGER.debug("Custom loader script does not define {}", invocation, e);
        }

        return compiled;
    }
}