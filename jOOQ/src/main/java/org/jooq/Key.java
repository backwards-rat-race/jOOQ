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

import java.util.List;

import org.jetbrains.annotations.NotNull;


/**
 * A <code>Key</code> is an object representing a <code>UNIQUE KEY</code>, a
 * <code>PRIMARY KEY</code>, or a <code>FOREIGN KEY</code>.
 * <p>
 * Instances of this type cannot be created directly. They are available from
 * generated code.
 *
 * @param <R> The <code>KEY</code>'s owner table record
 * @author Lukas Eder
 */
public interface Key<R extends Record> extends Named {

    /**
     * The <code>Key</code>'s owner table
     */
    @NotNull
    Table<R> getTable();

    /**
     * The fields that make up the <code>KEY</code>
     */
    @NotNull
    List<TableField<R, ?>> getFields();

    /**
     * The fields that make up the <code>KEY</code>
     *
     * @see #getFields()
     */
    @NotNull
    TableField<R, ?> @NotNull [] getFieldsArray();

    /**
     * Get this <code>KEY</code> as a formal {@link Constraint} specification.
     */
    @NotNull
    Constraint constraint();

    /**
     * Whether this key is being enforced.
     */
    boolean enforced();

    /**
     * Whether this key is (partially) nullable.
     */
    boolean nullable();
}
