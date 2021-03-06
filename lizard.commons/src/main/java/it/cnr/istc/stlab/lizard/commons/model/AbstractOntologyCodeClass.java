package it.cnr.istc.stlab.lizard.commons.model;

import it.cnr.istc.stlab.lizard.commons.model.types.OntologyCodeClassType;
import it.cnr.istc.stlab.lizard.commons.model.types.OntologyCodeMethodType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.jena.ontology.OntResource;

import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;

public abstract class AbstractOntologyCodeClass extends AbstractOntologyCodeEntity {

	protected OntologyCodeClassType ontologyClassType;
	protected Map<OntResource, Set<AbstractOntologyCodeMethod>> methodMap;
	protected AbstractOntologyCodeClass extendedClass;
	protected JClass jClass;

	protected AbstractOntologyCodeClass() {
		super();
		methodMap = new HashMap<OntResource, Set<AbstractOntologyCodeMethod>>();
	}

	protected AbstractOntologyCodeClass(OntResource ontResource, OntologyCodeModel ontologyModel, JCodeModel jCodeModel) {
		super(ontResource, ontologyModel, jCodeModel);
		methodMap = new HashMap<OntResource, Set<AbstractOntologyCodeMethod>>();
	}

	public Set<AbstractOntologyCodeMethod> getMethods(OntResource property) {
		return methodMap.get(property);
	}

	public AbstractOntologyCodeMethod getMethod(OntResource property, OntologyCodeMethodType type) {
		for (AbstractOntologyCodeMethod m : methodMap.get(property)) {
			if (m.getMethodType().equals(type)) {
				return m;
			}
		}
		return null;
	}

	protected abstract void extendsClasses(AbstractOntologyCodeClass oClass);

	public AbstractOntologyCodeClass getExtendedClass() {
		return extendedClass;
	}

	public void setExtendedClass(AbstractOntologyCodeClass extendedClass) {
		this.extendedClass = extendedClass;
	}

	public void addMethod(AbstractOntologyCodeMethod method) {
		Set<AbstractOntologyCodeMethod> methodSet = methodMap.get(method.getOntResource());
		if (methodSet == null) {
			methodSet = new HashSet<AbstractOntologyCodeMethod>();
			methodMap.put(method.getOntResource(), methodSet);
		}
		methodSet.add(method);
	}

	public Collection<AbstractOntologyCodeMethod> getMethods() {
		Collection<Set<AbstractOntologyCodeMethod>> methodSets = methodMap.values();

		Collection<AbstractOntologyCodeMethod> ontologyMethods = new ArrayList<AbstractOntologyCodeMethod>();
		for (Set<AbstractOntologyCodeMethod> methodSet : methodSets) {
			ontologyMethods.addAll(methodSet);
		}

		return ontologyMethods;
	}

	public OntologyCodeClassType getOntologyClassType() {
		return ontologyClassType;
	}

	public JClass asJDefinedClass() {
		return jClass;
	}

	public abstract Set<AbstractOntologyCodeClass> listSuperClasses();

}
