/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.asciidoctor.gradle.jvm.gems

import org.asciidoctor.gradle.jvm.gems.internal.FunctionalSpecification
import org.asciidoctor.gradle.testfixtures.jvm.CachingTest
import org.gradle.testkit.runner.BuildResult
import spock.lang.Unroll

class AsciidoctorGemPrepareTaskCachingFunctionalSpec extends FunctionalSpecification implements CachingTest {
    private static final String DEFAULT_TASK = 'asciidoctorGemsPrepare'
    private static final String OUTPUT_DIR_PATH = 'build/asciidoctorGems'
    private static final String DEFAULT_GEM_NAME = 'asciidoctor-revealjs'
    private static final String DEFAULT_GEM_VERSION = '2.0.0'

    void setup() {
        setupCache()
    }

    void "gemPrepare task is cacheable and relocatable"() {
        given:
        getBuildFile("""
            dependencies {
                asciidoctorGems("rubygems:${DEFAULT_GEM_NAME}:${DEFAULT_GEM_VERSION}") {
                    exclude module: 'asciidoctor'
                }
            }
        """)

        when:
        assertDefaultTaskExecutes()

        then:
        outputFile.exists()

        when:
        assertDefaultTaskIsCachedAndRelocatable()

        then:
        outputFile.exists()
        outputFileInRelocatedDirectory.exists()
    }

    void "gemPrepare task is cached when only the output directory changes"() {
        given:
        getBuildFile("""
            dependencies {
                asciidoctorGems("rubygems:${DEFAULT_GEM_NAME}:${DEFAULT_GEM_VERSION}") {
                    exclude module: 'asciidoctor'
                }
            }
        """)

        when:
        assertDefaultTaskExecutes()

        then:
        outputFile.exists()

        when:
        changeBuildConfigurationTo("""
            dependencies {
                asciidoctorGems("rubygems:${DEFAULT_GEM_NAME}:${DEFAULT_GEM_VERSION}") {
                    exclude module: 'asciidoctor'
                }
            }

            asciidoctorGemsPrepare {
                outputDir 'build/asciidoc'
            }
        """)

        then:
        assertDefaultTaskIsCachedAndRelocatable()

        and:
        file("build/asciidoc/gems/${DEFAULT_GEM_NAME}-${DEFAULT_GEM_VERSION}")
        fileInRelocatedDirectory("build/asciidoc/gems/${DEFAULT_GEM_NAME}-${DEFAULT_GEM_VERSION}")
    }

    void "gemPrepare task is not cached when gems change"() {
        String alternateGemName = 'json'
        String alternatGemVersion = '1.8.0'
        String alternateGemPath = "${OUTPUT_DIR_PATH}/gems/${alternateGemName}-${alternatGemVersion}-java"

        given:
        getBuildFile("""
            dependencies {
                asciidoctorGems("rubygems:${DEFAULT_GEM_NAME}:${DEFAULT_GEM_VERSION}") {
                    exclude module: 'asciidoctor'
                }
            }
        """)

        when:
        assertDefaultTaskExecutes()

        then:
        outputFile.exists()

        when:
        changeBuildConfigurationTo("""
            dependencies {
                asciidoctorGems("rubygems:${alternateGemName}:${alternatGemVersion}")
            }
        """)

        then:
        assertDefaultTaskExecutes()

        then:
        assertDefaultTaskIsCachedAndRelocatable()

        then:
        file(alternateGemPath).exists()
        fileInRelocatedDirectory(alternateGemPath).exists()
    }

    @Unroll
    void 'there are no deprecated usages with Gradle v#version'() {
        given:
        getBuildFile("""
            dependencies {
                asciidoctorGems("rubygems:${DEFAULT_GEM_NAME}:${DEFAULT_GEM_VERSION}") {
                    exclude module: 'asciidoctor'
                }
            }
        """)

        when:
        BuildResult result = getGradleRunner([DEFAULT_TASK, '--warning-mode', 'all'])
                .withGradleVersion(version)
                .build()

        and:
        // This property comes from a task in the jruby plugin
        allowDeprecation('Property \'dependencies\' is not annotated with an input or output annotation.')
        // This is due to the gem repository used in the test (does not appear to be an https alternative)
        allowDeprecation('Using insecure protocols with repositories has been deprecated.')

        then:
        assertNoDeprecatedUsages(result)

        where:
        version << ['5.6.4', '6.0.1']
    }

    @Override
    File getBuildFile(String extraContent) {
        File buildFile = testProjectDir.newFile('build.gradle')
        buildFile << """
            plugins {
                id 'org.asciidoctor.jvm.gems'
            }

            ${ -> scan ? buildScanConfiguration : '' }
            ${offlineRepositories}

            repositories {
                maven { 
                    url 'http://rubygems-proxy.torquebox.org/releases'
                    try {
                        allowInsecureProtocol = true
                    } catch (Exception ignore) {
                        // available for Gradle 6.0+
                    }
                }
            }

           ${extraContent}
        """
        buildFile
    }

    @Override
    File getOutputFile() {
        file("${OUTPUT_DIR_PATH}/gems/${DEFAULT_GEM_NAME}-${DEFAULT_GEM_VERSION}")
    }

    @Override
    String getDefaultTask() {
        ":${DEFAULT_TASK}"
    }
}