/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Other licenses:
 * -----------------------------------------------------------------------------
 * Commercial licenses for this work are available. These replace the above
 * ASL 2.0 and offer limited warranties, support, maintenance, and commercial
 * database integrations.
 *
 * For more information, please visit: http://www.jooq.org/licenses
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */
package org.jooq;

import static java.util.Comparator.naturalOrder;
import static org.jooq.FilePattern.Sort.SEMANTIC;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.jooq.exception.IOException;
import org.jooq.tools.JooqLogger;

import org.jetbrains.annotations.ApiStatus.Internal;

/**
 * A utility class that can traverse a directory structure given some ant-style
 * file patterns, or classpath resources.
 * <p>
 * The following algorithm is applied when traversing sources:
 * <p>
 * <ul>
 * <li>If <code>pattern</code> is a valid classpath resource, load the single
 * {@link Source} from there</li>
 * <li>If <code>pattern</code> is a valid file on the file system, load the
 * single {@link Source} from there</li>
 * <li>Match all files on the file system according to <code>pattern</code>
 * (interpreted as an ant-style file pattern), and load all of the
 * {@link Source} items given {@link #sort()}. An example pattern is
 * <code>src/main/resources/&#42;&#42;/&#42;.sql</code></li>
 * </ul>
 * <p>
 * This is INTERNAL API. Please do not use directly as API may change
 * incompatibly.
 *
 * @author Lukas Eder
 */
@Internal
public final class FilePattern {

    private static final JooqLogger log = JooqLogger.getLogger(FilePattern.class);

    private final Sort              sort;
    private final Comparator<File>  comparator;
    private final File              basedir;
    private final String            pattern;
    private final String            encoding;
    private final Pattern           regexForMatches;
    private final Pattern           regexForLoad;

    public FilePattern() {
        this(
            (Sort) null,
            (File) null,
            "**",
            "UTF-8"
        );
    }

    private FilePattern(
        Sort sort,
        File basedir,
        String pattern,
        String encoding
    ) {
        this.sort = sort;
        this.comparator = fileComparator(sort);
        this.basedir = basedir == null ? new File(".") : basedir;
        this.pattern = pattern;
        this.encoding = encoding;

        this.regexForMatches = Pattern.compile("^" + regex() + "$");
        this.regexForLoad = Pattern.compile("^.*?" + regex() + "$");
    }

    public final Sort sort() {
        return sort;
    }

    public final FilePattern sort(Sort newSort) {
        return new FilePattern(
            newSort,
            basedir,
            pattern,
            encoding
        );
    }

    public final File basedir() {
        return basedir;
    }

    public final FilePattern basedir(File newBasedir) {
        return new FilePattern(
            sort,
            newBasedir,
            pattern,
            encoding
        );
    }

    public final String pattern() {
        return pattern;
    }

    public final FilePattern pattern(String newPattern) {
        return new FilePattern(
            sort,
            basedir,
            newPattern,
            encoding
        );
    }

    public final String encoding() {
        return encoding;
    }

    public final FilePattern encoding(String newEncoding) {
        return new FilePattern(
            sort,
            basedir,
            pattern,
            newEncoding
        );
    }

    private static final Comparator<File> fileComparator(Sort sort) {
        if (sort == null)
            sort = SEMANTIC;

        switch (sort) {
            case ALPHANUMERIC:
                return naturalOrder();
            case NONE:
                return null;
            case FLYWAY:
                return FlywayFileComparator.INSTANCE;
            case SEMANTIC:
                return FileComparator.INSTANCE;
            default:
                throw new IllegalArgumentException("Unsupported sort: " + sort);
        }
    }

    /**
     * Whether a given path matches the pattern.
     */
    public final boolean matches(String path) {
        return regexForMatches.matcher(path.replace("\\", "/")).matches();
    }

    /**
     * Retrieve a set of {@link Source} items from this pattern.
     *
     * @throws IOException if something goes wrong while loading file contents.
     */
    public final List<Source> collect() {
        final List<Source> list = new ArrayList<>();
        load(list::add);
        return list;
    }

    /**
     * Load a set of {@link Source} items from this pattern.
     *
     * @throws IOException if something goes wrong while loading file contents.
     */
    public final void load(Consumer<Source> loader) {
        boolean loaded = false;
        URL url = null;

        try {
            url = FilePattern.class.getResource(pattern);
        }

        // [#10143] Starting with Java 7, and especially when running on the module path,
        //          there could be an InvalidPathException here.
        catch (Exception ignore) {}

        File file = null;

        try {
            if (url != null) {
                log.info("Reading from classpath: " + pattern);

                // [#11637] Avoid working with File to support classpath resources
                //          inside of nested jar files.
                try (InputStream is = FilePattern.class.getResourceAsStream(pattern)) {

                    // Cannot accept a Source based on an InputStream if using FilePattern::collect
                    loader.accept(Source.of(Source.of(is).readString()));
                    loaded = true;
                }
            }
            else {
                file = new File(pattern);

                if (file.exists()) {
                    load(file, comparator, null, loader);
                    loaded = true;
                }
                else if (!pattern.contains("*") && !pattern.contains("?")) {
                    load(new File(basedir, pattern), comparator, null, loader);
                    loaded = true;
                }
                else {

                    // [#9726] The wildcard could be in the middle of a path segment, which
                    //         has to be ignored, e.g. the prefix of a/b*/c is a/
                    String prefix = pattern.replaceAll("[^\\/]*?[*?].*", "");
                    file = new File(prefix);

                    if (!file.isAbsolute())
                        file = new File(basedir, prefix).getAbsoluteFile();

                    load(file, comparator, regexForLoad, loader);
                    loaded = true;
                }
            }
        }
        catch (java.io.IOException e) {
            throw new IOException("Error while loading pattern", e);
        }

        if (!loaded)
            log.error("Could not find source(s) : " + pattern);
    }

    private String regex() {
        return pattern
            .replace("\\", "/")
            .replace(".", "\\.")
            .replace("?", "[^/]")
            .replace("**", ".+?")
            .replace("*", "[^/]*");
    }

    private final void load(
        File file,
        Comparator<File> fileComparator,
        Pattern regex,
        Consumer<Source> loader
    ) throws java.io.IOException {
        if (file.isFile()) {
            if (regex == null || regex.matcher(file.getCanonicalPath().replace("\\", "/")).matches()) {
                log.info("Reading from: " + file + " [*]");
                load0(file, loader);
            }
        }
        else if (file.isDirectory()) {
            log.info("Reading from: " + file);

            File[] files = file.listFiles();

            if (files != null) {
                if (fileComparator != null)
                    Arrays.sort(files, fileComparator);

                for (File f : files)
                    load(f, comparator, regex, loader);
            }
        }
    }

    private final void load0(File file, Consumer<Source> loader) {
        try {
            loader.accept(Source.of(file, encoding));
        }
        catch (RuntimeException e) {
            log.error("Error while loading file: " + file);
            throw e;
        }
    }

    @Override
    public String toString() {
        return pattern;
    }

    /**
     * The sort algorithm to be applied to directory contents.
     */
    public enum Sort {

        /**
         * Semantic, version aware sorting (the default).
         * <p>
         * For example:
         *
         * <pre>
         * version-1
         * version-2
         * version-10
         * </pre>
         */
        SEMANTIC,

        /**
         * Standard alphanumeric sorting.
         * <p>
         * For example:
         *
         * <pre>
         * version-1
         * version-10
         * version-2
         * </pre>
         */
        ALPHANUMERIC,

        /**
         * Flyway compatible sorting.
         */
        FLYWAY,

        /**
         * No explicit sorting (may be non deterministic, depending on the file
         * system).
         */
        NONE;

        public static final Sort of(String sort) {
            if ("alphanumeric".equals(sort))
                return ALPHANUMERIC;
            else if ("none".equals(sort))
                return NONE;
            else if ("flyway".equals(sort))
                return FLYWAY;
            else
                return SEMANTIC;
        }
    }
}
