/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

package org.docx4j.org.apache.poi.hpsf;

/**
 * <p>This exception is thrown if an {@link java.io.InputStream} does
 * not support the {@link java.io.InputStream#mark} operation.</p>
 *
 * @author Rainer Klute <a
 * href="mailto:klute@rainer-klute.de">&lt;klute@rainer-klute.de&gt;</a>
 */
public class MarkUnsupportedException extends HPSFException
{

    /**
     * <p>Constructor</p>
     */
    public MarkUnsupportedException()
    {
        super();
    }


    /**
     * <p>Constructor</p>
     *
     * @param msg The exception's message string
     */
    public MarkUnsupportedException(final String msg)
    {
        super(msg);
    }


    /**
     * <p>Constructor</p>
     *
     * @param reason This exception's underlying reason
     */
    public MarkUnsupportedException(final Throwable reason)
    {
        super(reason);
    }


   /**
    * <p>Constructor</p>
    *
    * @param msg The exception's message string
    * @param reason This exception's underlying reason
    */
    public MarkUnsupportedException(final String msg, final Throwable reason)
    {
        super(msg, reason);
    }

}
