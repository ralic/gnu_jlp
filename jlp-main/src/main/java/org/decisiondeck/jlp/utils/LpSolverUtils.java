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
package org.decisiondeck.jlp.utils;

import java.util.Map;
import java.util.Set;

import org.decisiondeck.jlp.LpConstraint;
import org.decisiondeck.jlp.LpLinear;
import org.decisiondeck.jlp.LpSolverException;
import org.decisiondeck.jlp.LpTerm;
import org.decisiondeck.jlp.parameters.LpParameters;
import org.decisiondeck.jlp.parameters.LpParametersUtils;
import org.decisiondeck.jlp.problem.LpProblem;
import org.decisiondeck.jlp.problem.LpProblemUtils;
import org.decisiondeck.jlp.problem.LpProblemWithTransformedBoolsView;
import org.decisiondeck.jlp.problem.LpVariableType;
import org.decisiondeck.jlp.solution.LpSolution;

import com.google.common.base.Equivalence;
import com.google.common.base.Equivalences;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableBiMap.Builder;
import com.google.common.collect.Iterables;

/**
 * This class defines static methods that should be mainly useful for internal use in this project and to implement
 * underlying solvers.
 * 
 * @author Olivier Cailloux
 * 
 */
public class LpSolverUtils {

    /**
     * A function that applies the {@link #toString()} method to its input. Does not accept <code>null</code> values as
     * input.
     * 
     * @author Olivier Cailloux
     * 
     * @param <T>
     *            the type of input.
     */
    static public class ToString<T> implements Function<T, String> {
	@Override
	public String apply(T input) {
	    return input.toString();
	}
    }

    /**
     * Ensures that the given parameters are conform to the given mandatory values. That is, for each parameter value
     * that is mandatory, ensures that the given parameters have an associated value (which may be the default value)
     * that is equal to the mandatory value.
     * 
     * @param parameters
     *            not <code>null</code>.
     * @param mandatoryValues
     *            not <code>null</code>, no <code>null</code> key. The values must be meaningful.
     * @throws LpSolverException
     *             if the parameters are not conform.
     */
    public static void assertConform(LpParameters parameters, Map<Enum<?>, Object> mandatoryValues)
	    throws LpSolverException {
	for (Enum<?> parameter : LpParametersUtils.getParameters()) {
	    if (mandatoryValues.containsKey(parameter)) {
		final Object mandatoryValue = mandatoryValues.get(parameter);
		final Object value = parameters.getValueAsObject(parameter);
		if (!Equivalences.equals().equivalent(value, mandatoryValue)) {
		    throw new LpSolverException("Unsupported parameter value: " + parameter + ", " + value + ".");
		}
	    }
	}
    }

    /**
     * Ensures that the given problem represents a zero-one problem, thus that each variable in the problem either has
     * type {@link LpVariableType#BOOL} or has type {@link LpVariableType#INT} with bounds defined between 0 and 1
     * (inclusive).
     * 
     * @param <T>
     *            the class used for the variables in the problem.
     * 
     * @param problem
     *            not <code>null</code>.
     * @throws LpSolverException
     *             iff the problem is not zero-one.
     */
    static public <T> void assertIntZeroOne(final LpProblem<T> problem) throws LpSolverException {
	final LpProblem<T> problemNoBool = LpSolverUtils.getViewWithTransformedBools(problem);
	for (T variable : problemNoBool.getVariables()) {
	    LpVariableType type = problemNoBool.getVarType(variable);
	    if (type == LpVariableType.REAL) {
		throw new LpSolverException("Variable " + variable
			+ " is not an integer variable, this is not a zero-one problem.");
	    }
	    final Number lowerBound = problemNoBool.getVarLowerBound(variable);
	    final Number upperBound = problemNoBool.getVarUpperBound(variable);
	    if (lowerBound == null || lowerBound.doubleValue() < 0d) {
		throw new LpSolverException("Variable " + variable
			+ " has an inadequate lower bound, this is not a zero-one problem.");
	    }
	    if (upperBound == null || upperBound.doubleValue() > 1d) {
		throw new LpSolverException("Variable " + variable
			+ " has an inadequate upper bound, this is not a zero-one problem.");
	    }
	}
    }

    static public <T> boolean boolsAreBools(LpSolution<T> solution) {
	final Set<T> bools = LpProblemUtils.getVariables(solution.getProblem(), LpVariableType.BOOL);
	for (T bool : bools) {
	    final Number value = solution.getValue(bool);
	    final boolean clean;
	    if (value == null) {
		clean = true;
	    } else {
		final double val = value.doubleValue();
		if (val < -1e-6) {
		    clean = false;
		} else if (val > 1e-6 && val < 1 - 1e-6) {
		    clean = false;
		} else if (val > 1 + 1e-6) {
		    clean = false;
		} else {
		    clean = true;
		}
	    }
	    if (!clean) {
		return false;
	    }
	}
	return true;
    }

    /**
     * Completely erase the target data and replace it with the data in the source problem. The order of the variables
     * and constraints in target is set to be the same as the order of the source, thus the variables and constraints
     * sets iteration order of the target will be the same as the sets iteration order of the source. When this method
     * returns the target is identical to the source.
     * 
     * @param <T>
     *            the type of the variables objects.
     * 
     * @param source
     *            not <code>null</code>.
     * @param target
     *            not <code>null</code>.
     * 
     * @return <code>true</code> if the state of the target object changed as a result of this call. Equivalently,
     *         <code>false</code> iff the given source equals the given target.
     */
    public static <T> boolean copyProblemTo(LpProblem<T> source, LpProblem<T> target) {
	Preconditions.checkNotNull(target);
	Preconditions.checkNotNull(source);
	if (source.equals(target)) {
	    return false;
	}
	target.clear();
	target.setName(source.getName());
	for (T variable : source.getVariables()) {
	    target.setVarType(variable, source.getVarType(variable));
	    target.setVarName(variable, source.getVarNameSet(variable));
	    target.setVarBounds(variable, source.getVarLowerBound(variable), source.getVarUpperBound(variable));
	}
	target.setObjective(source.getObjective().getFunction(), source.getObjective().getDirection());
	for (LpConstraint<T> constraint : source.getConstraints()) {
	    target.add(constraint);
	}
	target.setVarNamer(source.getVarNamer());
	target.setConstraintsNamer(source.getConstraintsNamer());
	return true;
    }

    static public boolean equivalent(LpConstraint<?> a, LpConstraint<?> b) {
	return getConstraintEquivalence().equivalent(a, b);
    }

    static public boolean equivalent(LpLinear<?> a, LpLinear<?> b) {
	return getLinearEquivalence().equivalent(a, b);
    }

    static public boolean equivalent(LpProblem<?> a, LpProblem<?> b) {
	return getProblemEquivalence().equivalent(a, b);
    }

    static public boolean equivalent(LpSolution<?> a, LpSolution<?> b) {
	return getSolutionEquivalence().equivalent(a, b);
    }

    static public int getAsInteger(double number) throws LpSolverException {
	final long lValue = Math.round(number);
	if (lValue > Integer.MAX_VALUE) {
	    throw new LpSolverException("Number " + number + " does not fit into an integer (too big).");
	}
	final int iValue = (int) lValue;

	if (Math.abs(number - iValue) > 1e-6) {
	    throw new LpSolverException("Number " + number + " does not round to an integer.");
	}

	return iValue;
    }

    /**
     * Provides an implementation of toString for debugging use.
     * 
     * @param <T>
     *            the type of variable.
     * @param constraint
     *            not <code>null</code>.
     * @return a debug description.
     */
    static public <T> String getAsString(LpConstraint<T> constraint) {
	final ToStringHelper helper = Objects.toStringHelper(constraint);
	helper.addValue('\'' + constraint.getName() + '\'');
	helper.addValue(constraint.getLhs().toString() + constraint.getOperator() + constraint.getRhs());
	return helper.toString();
    }

    /**
     * Provides an implementation of toString for debugging use. For a more user friendly string description, see class
     * {@link LpProblemUtils}.
     * 
     * @param <T>
     *            the type of variable.
     * @param problem
     *            not <code>null</code>.
     * @return a debug description.
     */
    static public <T> String getAsString(LpProblem<T> problem) {
	final ToStringHelper helper = Objects.toStringHelper(problem);
	helper.addValue('\'' + problem.getName() + '\'');
	if (!problem.getObjective().isEmpty()) {
	    helper.addValue("" + problem.getObjective().getDirection() + " " + problem.getObjective().getFunction());
	}
	helper.addValue("" + problem.getVariables().size() + " variables");
	helper.addValue(problem.getConstraints().size() + " constraints");
	return helper.toString();
    }

    static public <T> String getAsString(LpSolution<T> solution) {
	final ToStringHelper helper = Objects.toStringHelper(solution);
	helper.add("Problem", solution.getProblem());
	helper.add("Objective value", solution.getObjectiveValue());
	helper.add("Valued variables size", Integer.valueOf(solution.getVariables().size()));
	return helper.toString();
    }

    static public Equivalence<LpConstraint<?>> getConstraintEquivalence() {
	return new Equivalence<LpConstraint<?>>() {

	    @Override
	    public boolean doEquivalent(LpConstraint<?> a, LpConstraint<?> b) {
		if (a.getRhs() != b.getRhs()) {
		    return false;
		}
		if (!a.getLhs().equals(b.getLhs())) {
		    return false;
		}
		if (!a.getOperator().equals(b.getOperator())) {
		    return false;
		}
		return true;
	    }

	    @Override
	    public int doHash(LpConstraint<?> c) {
		return Objects.hashCode(c.getLhs(), c.getOperator(), Double.valueOf(c.getRhs()));
	    }
	};
    }

    static public Equivalence<Number> getEquivalenceByDoubleValue() {
	return new Equivalence<Number>() {
	    @Override
	    public boolean doEquivalent(Number a, Number b) {
		return a.doubleValue() == b.doubleValue();
	    }

	    @Override
	    public int doHash(Number t) {
		return Double.valueOf(t.doubleValue()).hashCode();
	    }
	};
    }

    static public Equivalence<LpLinear<?>> getLinearEquivalence() {
	return new Equivalence<LpLinear<?>>() {
	    @Override
	    public boolean doEquivalent(LpLinear<?> a, LpLinear<?> b) {
		return Iterables.elementsEqual(a, b);
	    }

	    @Override
	    public int doHash(LpLinear<?> t) {
		int hashCode = 1;
		for (LpTerm<?> term : t) {
		    hashCode = 31 * hashCode + term.hashCode();
		}
		return hashCode;
	    }
	};
    }

    static public Equivalence<LpProblem<? extends Object>> getProblemEquivalence() {
	return new Equivalence<LpProblem<? extends Object>>() {
	    @Override
	    public boolean doEquivalent(LpProblem<? extends Object> a, LpProblem<? extends Object> b) {
		return computeEquivalent(a, b);
	    }

	    private <T1, T2> boolean computeEquivalent(LpProblem<T1> a, LpProblem<T2> b) {
		if (!a.getConstraints().equals(b.getConstraints())) {
		    return false;
		}
		if (!Objects.equal(a.getObjective(), b.getObjective())) {
		    return false;
		}
		if (!a.getVariables().equals(b.getVariables())) {
		    return false;
		}
		for (T1 variable : a.getVariables()) {
		    if (!b.getVariables().contains(variable)) {
			return false;
		    }
		    @SuppressWarnings("unchecked")
		    final T2 varTyped = (T2) variable;

		    if (!getEquivalenceByDoubleValue().equivalent(a.getVarLowerBound(variable),
			    b.getVarLowerBound(varTyped))) {
			return false;
		    }
		    if (!Objects.equal(a.getVarType(variable), b.getVarType(varTyped))) {
			return false;
		    }
		    if (!getEquivalenceByDoubleValue().equivalent(a.getVarUpperBound(variable),
			    b.getVarUpperBound(varTyped))) {
			return false;
		    }
		}
		return true;
	    }

	    @Override
	    public int doHash(LpProblem<? extends Object> t) {
		final int hashCode = Objects.hashCode(t.getObjective());
		return hashCode + t.getConstraints().hashCode() + t.getVariables().hashCode();
	    }
	};
    }

    static public Equivalence<LpSolution<? extends Object>> getSolutionEquivalence() {
	return new Equivalence<LpSolution<? extends Object>>() {
	    @Override
	    public boolean doEquivalent(LpSolution<? extends Object> a, LpSolution<? extends Object> b) {
		return computeEquivalent(a, b);
	    }

	    private <T1, T2> boolean computeEquivalent(LpSolution<T1> a, LpSolution<T2> b) {
		if (!getEquivalenceByDoubleValue().equivalent(a.getObjectiveValue(), b.getObjectiveValue())) {
		    return false;
		}
		if (!a.getProblem().equals(b.getProblem())) {
		    return false;
		}
		for (T1 variable : a.getVariables()) {
		    if (!b.getVariables().contains(variable)) {
			return false;
		    }
		    @SuppressWarnings("unchecked")
		    final T2 varTyped = (T2) variable;

		    if (!getEquivalenceByDoubleValue().equivalent(a.getValue(variable), b.getValue(varTyped))) {
			return false;
		    }
		}
		for (LpConstraint<T1> constraint : a.getConstraints()) {
		    if (!b.getConstraints().contains(constraint)) {
			return false;
		    }
		    @SuppressWarnings("unchecked")
		    final LpConstraint<T2> constraintTyped = (LpConstraint<T2>) constraint;

		    if (!getEquivalenceByDoubleValue().equivalent(a.getDualValue(constraint),
			    b.getDualValue(constraintTyped))) {
			return false;
		    }
		}
		return true;
	    }

	    @Override
	    public int doHash(LpSolution<? extends Object> t) {
		return computeHash(t);
	    }

	    private <T> int computeHash(LpSolution<T> solution) {
		int hashCode = Objects.hashCode(solution.getProblem(), solution.getProblem());
		for (T variable : solution.getVariables()) {
		    hashCode += solution.getValue(variable).hashCode();
		}
		for (LpConstraint<T> constraint : solution.getConstraints()) {
		    hashCode += solution.getDualValue(constraint).hashCode();
		}
		return hashCode;
	    }
	};
    }

    static public <T> BiMap<T, Integer> getVariablesIds(LpProblem<T> problem, int startId) {
	Preconditions.checkNotNull(problem);
	final Builder<T, Integer> builder = ImmutableBiMap.builder();
	{
	    int i = startId;
	    for (T variable : problem.getVariables()) {
		builder.put(variable, Integer.valueOf(i));
		++i;
	    }
	}
	final ImmutableBiMap<T, Integer> variableIds = builder.build();
	return variableIds;
    }

    /**
     * <p>
     * Retrieves the bound of the variable from the given problem, with a possible modification if the variable type is
     * {@link LpVariableType#BOOL}: the bound is itself <em>bounded</em> to zero.
     * </p>
     * <p>
     * Consider a variable defined in the delegate problem having the type {@link LpVariableType#BOOL} and a lower bound
     * <em>l</em>. This method will return as its lower bound 0 if l is <code>null</code>, 0 if l.doubleValue() is lower
     * than zero, and l otherwise. E.g. this method returns zero as the lower bound of a {@link LpVariableType#BOOL}
     * variable having a lower bound of -1 in the given problem.
     * </p>
     * 
     * @see #getViewWithTransformedBools(LpProblem)
     * 
     * @param <T>
     *            the class of variables used in the problem.
     * @param problem
     *            not <code>null</code>.
     * @param variable
     *            must exist in the problem.
     * @return the bound of the variable according to the given problem. The bound is not <code>null</code> and greater
     *         or equal to zero if the variable has the type {@link LpVariableType#BOOL} according to the given problem.
     */
    static public <T> Number getVarLowerBoundBounded(LpProblem<T> problem, T variable) {
	Preconditions.checkArgument(problem.getVariables().contains(variable));
	final LpVariableType type = problem.getVarType(variable);
	if (type != LpVariableType.BOOL) {
	    return problem.getVarLowerBound(variable);
	}
	final Number low = problem.getVarLowerBound(variable);
	if (low == null || low.doubleValue() < 0d) {
	    return Double.valueOf(0d);
	}
	return low;
    }

    /**
     * <p>
     * Retrieves the bound of the variable from the given problem, with a possible modification if the variable type is
     * {@link LpVariableType#BOOL}: the bound is itself <em>bounded</em> to one.
     * </p>
     * <p>
     * Consider a variable defined in the delegate problem having the type {@link LpVariableType#BOOL} and an upper
     * bound <em>u</em>. This method will return as its upper bound 1 if u is <code>null</code>, 1 if u.doubleValue() is
     * greater than one, and u otherwise. E.g. this method returns 1 as the upper bound of a {@link LpVariableType#BOOL}
     * variable having an upper bound of 1.5 in the given problem.
     * </p>
     * 
     * @see #getViewWithTransformedBools(LpProblem)
     * 
     * @param <T>
     *            the class of variables used in the problem.
     * @param problem
     *            not <code>null</code>.
     * @param variable
     *            must exist in the problem.
     * @return the bound of the variable according to the given problem. The bound is not <code>null</code> and greater
     *         or equal to zero if the variable has the type {@link LpVariableType#BOOL} according to the given problem.
     */
    static public <T> Number getVarUpperBoundBounded(LpProblem<T> problem, T variable) {
	Preconditions.checkArgument(problem.getVariables().contains(variable));
	final LpVariableType type = problem.getVarType(variable);
	if (type != LpVariableType.BOOL) {
	    return problem.getVarUpperBound(variable);
	}
	final Number up = problem.getVarUpperBound(variable);
	if (up == null || up.doubleValue() > 0d) {
	    return Double.valueOf(1d);
	}
	return up;
    }

    static public <T> LpProblem<T> getViewWithTransformedBools(LpProblem<T> problem) {
	return new LpProblemWithTransformedBoolsView<T>(problem);
    }
}
