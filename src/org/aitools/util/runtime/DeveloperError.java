/*
 * aitools utilities
 * Copyright (C) 2006 Noel Bush
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.

 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.aitools.util.runtime;

/**
 * A developer error.
 * 
 * @author <a href="mailto:noel@aitools.org">Noel Bush</a>
 */
public class DeveloperError extends Error {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;

  /**
   * Creates a new DeveloperError associated with the given Throwable.
   * 
   * @param message the message describing the error
   * @param e the Throwable that is responsible for this DeveloperError
   */
  public DeveloperError(String message, Throwable e) {
    super(message);
    this.initCause(e);
  }
}
