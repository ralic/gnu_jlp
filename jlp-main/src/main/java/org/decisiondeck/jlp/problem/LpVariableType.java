/**
 * Copyright © 2010-2011 École Centrale Paris
 *
 * 	This file is part of JLP.
 *
 * 	JLP is free software: you can redistribute it and/or modify it under the
 * 	terms of the GNU Lesser General Public License version 3 as published by
 * 	the Free Software Foundation.
 *
 * 	JLP is distributed in the hope that it will be useful, but WITHOUT ANY
 * 	WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * 	FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * 	more details.
 *
 * 	You should have received a copy of the GNU Lesser General Public License
 * 	along with JLP. If not, see <http://www.gnu.org/licenses/>.
 */
package org.decisiondeck.jlp.problem;

/**
 * Associated with a variable, defines the range of values the variable may take.
 * 
 * @author Olivier Cailloux
 * 
 */
public enum LpVariableType {

    /**
     * Boolean: a zero or one value.
     */
    BOOL,
    /**
     * Integer
     */
    INT,
    /**
     * Real
     */
    REAL;

    public boolean isInt() {
	switch (this) {
	case BOOL:
	case INT:
	    return true;
	case REAL:
	    return false;
	}
	throw new IllegalStateException("Unknown type.");
    }
}
