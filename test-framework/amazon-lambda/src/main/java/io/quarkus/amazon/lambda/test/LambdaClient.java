package io.quarkus.amazon.lambda.test;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;

public class LambdaClient {

    private static final AtomicInteger REQUEST_ID_GENERATOR = new AtomicInteger();
    public static final ConcurrentHashMap<String, CompletableFuture<String>> REQUESTS;
    public static final LinkedBlockingDeque<Map.Entry<String, String>> REQUEST_QUEUE;
    static volatile LambdaException problem;

    static {
        //a hack around class loading
        //this is always loaded in the root class loader with jboss-logmanager,
        //however it may also be loaded in an isolated CL when running in dev
        //or test mode. If it is in an isolated CL we load the handler from
        //the class on the system class loader so they are equal
        //TODO: should this class go in its own module and be excluded from isolated class loading?
        ConcurrentHashMap<String, CompletableFuture<String>> requests = new ConcurrentHashMap<>();
        LinkedBlockingDeque<Map.Entry<String, String>> requestQueue = new LinkedBlockingDeque<>();
        ClassLoader cl = LambdaClient.class.getClassLoader();
        try {
            Class<?> root = Class.forName(LambdaClient.class.getName(), false, ClassLoader.getSystemClassLoader());
            if (root.getClassLoader() != cl) {
                requestQueue = (LinkedBlockingDeque<Map.Entry<String, String>>) root.getDeclaredField("REQUEST_QUEUE")
                        .get(null);
                requests = (ConcurrentHashMap<String, CompletableFuture<String>>) root.getDeclaredField("REQUESTS").get(null);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        REQUESTS = requests;
        REQUEST_QUEUE = requestQueue;

    }

    public static void invoke(InputStream inputStream, OutputStream outputStream) {
        if (problem != null) {
            throw new RuntimeException(problem);
        }
        try {
            String id = "aws-request-" + REQUEST_ID_GENERATOR.incrementAndGet();
            CompletableFuture<String> result = new CompletableFuture<>();
            REQUESTS.put(id, result);
            StringBuilder requestBody = new StringBuilder();
            int i = 0;
            while ((i = inputStream.read()) != -1) {
                requestBody.append((char) i);
            }
            REQUEST_QUEUE.add(new Map.Entry<String, String>() {

                @Override
                public String getKey() {
                    return id;
                }

                @Override
                public String getValue() {
                    return requestBody.toString();
                }

                @Override
                public String setValue(String value) {
                    return null;
                }
            });
            String output = result.get();
            outputStream.write(output.getBytes());
        } catch (Exception e) {
            if (e instanceof ExecutionException) {
                Throwable ex = e.getCause();
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                }
                throw new RuntimeException(ex);
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * Marshalls input and output as JSON using Jackson
     *
     * @param returnType
     * @param input
     * @param <T>
     * @return
     */
    public static <T> T invoke(Class<T> returnType, Object input) {
        if (problem != null) {
            throw new RuntimeException(problem);
        }
        try {
            final ObjectMapper mapper = getObjectMapper();
            String id = "aws-request-" + REQUEST_ID_GENERATOR.incrementAndGet();
            CompletableFuture<String> result = new CompletableFuture<>();
            REQUESTS.put(id, result);
            String requestBody = mapper.writeValueAsString(input);
            REQUEST_QUEUE.add(new Map.Entry<String, String>() {

                @Override
                public String getKey() {
                    return id;
                }

                @Override
                public String getValue() {
                    return requestBody;
                }

                @Override
                public String setValue(String value) {
                    return null;
                }
            });
            String output = result.get();
            return mapper.readerFor(returnType).readValue(output);
        } catch (Exception e) {
            if (e instanceof ExecutionException) {
                Throwable ex = e.getCause();
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                }
                throw new RuntimeException(ex);
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * Takes a json string as input. Unmarshalls return value using Jackson.
     *
     * @param returnType
     * @param json
     * @param <T>
     * @return
     */
    public static <T> T invokeJson(Class<T> returnType, String json) {
        if (problem != null) {
            throw new RuntimeException(problem);
        }
        try {
            final ObjectMapper mapper = getObjectMapper();
            String id = "aws-request-" + REQUEST_ID_GENERATOR.incrementAndGet();
            CompletableFuture<String> result = new CompletableFuture<>();
            REQUESTS.put(id, result);
            REQUEST_QUEUE.add(new Map.Entry<String, String>() {

                @Override
                public String getKey() {
                    return id;
                }

                @Override
                public String getValue() {
                    return json;
                }

                @Override
                public String setValue(String value) {
                    return null;
                }
            });
            String output = result.get();
            return mapper.readerFor(returnType).readValue(output);
        } catch (Exception e) {
            if (e instanceof ExecutionException) {
                Throwable ex = e.getCause();
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                }
                throw new RuntimeException(ex);
            }
            throw new RuntimeException(e);
        }
    }

    private static ObjectMapper getObjectMapper() {
        ArcContainer container = Arc.container();
        if (container == null) {
            return new ObjectMapper();
        }
        InstanceHandle<ObjectMapper> instance = container.instance(ObjectMapper.class);
        if (instance.isAvailable()) {
            return instance.get();
        }
        return new ObjectMapper();
    }

}
