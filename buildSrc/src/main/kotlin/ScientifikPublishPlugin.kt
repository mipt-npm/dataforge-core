import com.jfrog.bintray.gradle.BintrayExtension
import groovy.lang.GroovyObject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.artifact.FileBasedMavenArtifact
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jfrog.gradle.plugin.artifactory.dsl.ArtifactoryPluginConvention
import org.jfrog.gradle.plugin.artifactory.dsl.PublisherConfig
import org.jfrog.gradle.plugin.artifactory.dsl.ResolverConfig


open class ScientifikExtension {
    var vcs = "https://github.com/altavir/dataforge-core"
    var bintrayRepo = "dataforge"
    var dokka = true
}

open class ScientifikPublishPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        project.plugins.apply("maven-publish")
        val extension = project.extensions.create<ScientifikExtension>("scientifik")



        project.configure<PublishingExtension> {
            repositories {
                maven("https://bintray.com/mipt-npm/${extension.bintrayRepo}")
            }

            // Process each publication we have in this project
            publications.filterIsInstance<MavenPublication>().forEach { publication ->

                @Suppress("UnstableApiUsage")
                publication.pom {
                    name.set(project.name)
                    description.set(project.description)
                    url.set(extension.vcs)

                    licenses {
                        license {
                            name.set("The Apache Software License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("MIPT-NPM")
                            name.set("MIPT nuclear physics methods laboratory")
                            organization.set("MIPT")
                            organizationUrl.set("http://npm.mipt.ru")
                        }

                    }
                    scm {
                        url.set(extension.vcs)
                    }
                }

                val moduleFile = project.buildDir.resolve("publications/${publication.name}/module.json")
                if (moduleFile.exists()) {
                    publication.artifact(object : FileBasedMavenArtifact(moduleFile) {
                        override fun getDefaultExtension() = "module"
                    })
                }
            }
        }

        if(extension.dokka){
            project.plugins.apply("org.jetbrains.dokka")

            project.afterEvaluate {
                extensions.findByType<KotlinMultiplatformExtension>()?.apply{
                    val dokka by tasks.getting(DokkaTask::class) {
                        outputFormat = "html"
                        outputDirectory = "$buildDir/javadoc"
                        jdkVersion = 8

                        kotlinTasks {
                            // dokka fails to retrieve sources from MPP-tasks so we only define the jvm task
                            listOf(tasks.getByPath("compileKotlinJvm"))
                        }
                        sourceRoot {
                            // assuming only single source dir
                            path = sourceSets["commonMain"].kotlin.srcDirs.first().toString()
                            platforms = listOf("Common")
                        }
                        // although the JVM sources are now taken from the task,
                        // we still define the jvm source root to get the JVM marker in the generated html
                        sourceRoot {
                            // assuming only single source dir
                            path = sourceSets["jvmMain"].kotlin.srcDirs.first().toString()
                            platforms = listOf("JVM")
                        }
                    }

                    val kdocJar by tasks.registering(Jar::class) {
                        group = JavaBasePlugin.DOCUMENTATION_GROUP
                        dependsOn(dokka)
                        archiveClassifier.set("javadoc")
                        from("$buildDir/javadoc")
                    }

                    configure<PublishingExtension> {

                        targets.all {
                            val publication = publications.findByName(name) as MavenPublication

                            // Patch publications with fake javadoc
                            publication.artifact(kdocJar.get())
                        }
                    }
                }


                extensions.findByType<KotlinJvmProjectExtension>()?.apply{
                    val dokka by tasks.getting(DokkaTask::class) {
                        outputFormat = "html"
                        outputDirectory = "$buildDir/javadoc"
                        jdkVersion = 8
                    }

                    val kdocJar by tasks.registering(Jar::class) {
                        group = JavaBasePlugin.DOCUMENTATION_GROUP
                        dependsOn(dokka)
                        archiveClassifier.set("javadoc")
                        from("$buildDir/javadoc")
                    }

                    configure<PublishingExtension> {
                        publications.filterIsInstance<MavenPublication>().forEach { publication ->
                            publication.artifact(kdocJar.get())
                        }
                    }
                }
            }
        }

        project.plugins.apply("com.jfrog.bintray")

        project.configure<BintrayExtension> {
            user = project.findProperty("bintrayUser") as? String ?: System.getenv("BINTRAY_USER")
            key = project.findProperty("bintrayApiKey") as? String? ?: System.getenv("BINTRAY_API_KEY")
            publish = true
            override = true // for multi-platform Kotlin/Native publishing

            // We have to use delegateClosureOf because bintray supports only dynamic groovy syntax
            // this is a problem of this plugin
            pkg.apply {
                userOrg = "mipt-npm"
                repo = extension.bintrayRepo
                name = project.name
                issueTrackerUrl = "${extension.vcs}/issues"
                setLicenses("Apache-2.0")
                vcsUrl = extension.vcs
                version.apply {
                    name = project.version.toString()
                    vcsTag = project.version.toString()
                    released = java.util.Date().toString()
                }
            }

            //workaround bintray bug
            project.afterEvaluate {
                setPublications(*project.extensions.findByType<PublishingExtension>()!!.publications.names.toTypedArray())
            }

//            project.tasks.figetByPath("bintrayUpload") {
//                    dependsOn(publishToMavenLocal)
//            }
        }

        project.plugins.apply("com.jfrog.artifactory")

        project.configure<ArtifactoryPluginConvention> {
            val artifactoryUser: String? by project
            val artifactoryPassword: String? by project
            val artifactoryContextUrl = "http://npm.mipt.ru:8081/artifactory"

            setContextUrl(artifactoryContextUrl)//The base Artifactory URL if not overridden by the publisher/resolver
            publish(delegateClosureOf<PublisherConfig> {
                repository(delegateClosureOf<GroovyObject> {
                    setProperty("repoKey", "gradle-dev-local")
                    setProperty("username", artifactoryUser)
                    setProperty("password", artifactoryPassword)
                })

                defaults(delegateClosureOf<GroovyObject> {
                    invokeMethod("publications", arrayOf("jvm", "js", "kotlinMultiplatform", "metadata"))
                })
            })
            resolve(delegateClosureOf<ResolverConfig> {
                repository(delegateClosureOf<GroovyObject> {
                    setProperty("repoKey", "gradle-dev")
                    setProperty("username", artifactoryUser)
                    setProperty("password", artifactoryPassword)
                })
            })
        }
    }
}