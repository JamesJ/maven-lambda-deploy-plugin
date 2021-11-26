package me.jamesj.lambdadeploy.plugin;

import com.google.common.reflect.ClassPath;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import me.jamesj.lambdadeploy.api.ApiFunction;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeResponse;

/**
 * Created by @James on 24/11/2021
 *
 * @author James
 * @since 24/11/2021
 */
@Mojo(name = "aws-deploy", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.COMPILE)
public class PluginMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true) MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    MavenSession mavenSession;

    @Parameter(defaultValue = "us-east-2") String region;

    @Parameter(defaultValue = "true") boolean autoPublish;

    @Parameter String credentials;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (mavenSession.isOffline()) {
            getLog().info("Maven build execution is offline, therefore will not attempt to contact AWS servers");
            return;
        }

        if ("pom".equals(project.getPackaging())) {
            getLog().debug("Ignoring pom packaging.");
            return;
        }

        AwsCredentialsProvider credentialsProvider;
        if (credentials != null && !credentials.isEmpty()) {
            getLog().info("Reading credentials from " + credentials);
            credentialsProvider = ProfileCredentialsProvider.builder().profileFile(ProfileFile.builder().content(Paths.get(credentials)).build())
                .build();
        } else {
            getLog().info("Using default environment credentials provider");
            credentialsProvider = EnvironmentVariableCredentialsProvider.create();
        }

        if (System.getenv("AWS_REGION") != null) {
            this.region = System.getenv("AWS_REGION");
        }

        LambdaClient lambdaClient = LambdaClient.builder()
            .region(Region.of(this.region))
            .credentialsProvider(credentialsProvider)
            .build();
        try {
            File artifact = project.getArtifact().getFile();

            resolveClasses().forEach(aClass -> {
                Annotation annotation = getAnnotation(aClass); // we're required to do hacky stuff because the loader is trash and proxies them
                String function;
                try {
                    function = invokeAnnotation(annotation);
                } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                    getLog().error("Failed to read function name for " + aClass, e);
                    return;
                }
                getLog().info("Processing function " + function + " in " + aClass.getName());

                SdkBytes bytes;
                try {
                    bytes = SdkBytes.fromInputStream(new FileInputStream(artifact));
                } catch (FileNotFoundException e) {
                    getLog().error("Failed to publish function \"" + function + "\" (from " + aClass.getName() + ")", e);
                    return;
                }

                UpdateFunctionCodeResponse response = lambdaClient.updateFunctionCode(builder -> {
                    builder.publish(autoPublish)
                        .functionName(function)
                        .zipFile(bytes);
                });

                getLog().info("Pushed to Lambda " + response);

            });
        } catch (Exception e) {
            getLog().error(e);
        }
    }

    private Collection<Class<?>> resolveClasses() throws IOException, DependencyResolutionRequiredException {
        final Set<URL> urls = new LinkedHashSet<>();

        for (String element : project.getCompileClasspathElements()) {
            urls.add(new File(element).toURI().toURL());
        }

        URLClassLoader classLoader = new URLClassLoader(new URL[]{
            new File(project.getBuild().getOutputDirectory()).toURI().toURL()
        }, new URLClassLoader(urls.toArray(URL[]::new)));

        return ClassPath.from(classLoader)
            .getTopLevelClasses()
            .stream()
            .filter(classInfo -> !classInfo.getName().startsWith("META-INF") && !classInfo.getName()
                .equals("module-info")) // prevent loading of META-INF/ files
            .map(classInfo -> classInfo.load())
            .filter(loaded -> {
                if (loaded.isEnum()) {
                    return false;
                }
                if (loaded.isInterface()) {
                    return false;
                }

                Annotation annotation = getAnnotation(loaded);

                return annotation != null;
            })
            .collect(Collectors.toList());
    }

    private Annotation getAnnotation(Class<?> object) {
        for (Annotation a : object.getAnnotations()) {
            if (a.annotationType().getCanonicalName().equals(ApiFunction.class.getCanonicalName())) {
                return a;
            }
        }
        return null;
    }

    private String invokeAnnotation(Annotation annotation)
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return (String) annotation.annotationType().getMethod("value").invoke(annotation);
    }
}
