package it.cnr.istc.stlab.lizard.commons;

import org.apache.jena.rdf.model.RDFNode;

public class InsufficientArgumentsForInstantiationException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private RDFNode individual;
	@SuppressWarnings("rawtypes")
	private Class<? extends ExtentionalLizardClassImpl>[] classes;

	@SuppressWarnings("rawtypes")
	public InsufficientArgumentsForInstantiationException(RDFNode individual, @SuppressWarnings("unchecked") Class<? extends ExtentionalLizardClassImpl>... classes) {
		this.individual = individual;
		this.classes = classes;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public String getMessage() {
		StringBuilder sb = new StringBuilder();
		sb.append("This class cannot be instantiate for individual ");
		sb.append(individual);
		sb.append(" as the following required arguments are not provided: ");

		boolean firstLoop = true;
		for (Class<? extends ExtentionalLizardClassImpl> c : classes) {
			if (!firstLoop)
				sb.append(", ");
			else
				firstLoop = false;

			sb.append(c.getName());
		}
		return sb.toString();
	}

}
