package it.cnr.istc.stlab.lizard.core.model;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.OWL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JConditional;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JForEach;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import it.cnr.istc.stlab.lizard.commons.Constants;
import it.cnr.istc.stlab.lizard.commons.LizardInterface;
import it.cnr.istc.stlab.lizard.commons.PrefixRegistry;
import it.cnr.istc.stlab.lizard.commons.model.AbstractOntologyCodeClass;
import it.cnr.istc.stlab.lizard.commons.model.AbstractOntologyCodeMethod;
import it.cnr.istc.stlab.lizard.commons.model.OntologyCodeClass;
import it.cnr.istc.stlab.lizard.commons.model.OntologyCodeInterface;
import it.cnr.istc.stlab.lizard.commons.model.OntologyCodeMethod;
import it.cnr.istc.stlab.lizard.commons.model.OntologyCodeModel;
import it.cnr.istc.stlab.lizard.commons.model.anon.BooleanAnonClass;
import it.cnr.istc.stlab.lizard.commons.model.types.OntologyCodeMethodType;
import it.cnr.istc.stlab.lizard.core.OntologyProjectGenerationRecipe;

public class RestOntologyCodeMethod extends OntologyCodeMethod {

	private static Logger logger = LoggerFactory.getLogger(RestOntologyCodeMethod.class);

	RestOntologyCodeMethod(OntologyCodeMethodType methodType, OntResource methodResource, AbstractOntologyCodeClass owner, Collection<AbstractOntologyCodeClass> domain, AbstractOntologyCodeClass range, OntologyCodeModel ontologyModel, JCodeModel codeModel) {
		super(methodType, methodResource, owner, domain, range, ontologyModel, codeModel);

		if (methodResource.isURIResource()) {

			String namespace = methodResource.getNameSpace();

			String prefix = ontologyModel.asOntModel().getNsURIPrefix(namespace);

			// look-up on prefix.cc
			if (prefix == null)
				prefix = PrefixRegistry.getInstance().getNsPrefix(namespace);
			// if the prefix is again null, then we create it
			if (prefix == null)
				prefix = PrefixRegistry.getInstance().createNsPrefix(namespace);

			String localName = Constants.getJavaName(methodResource.getLocalName());

			if (prefix.isEmpty())
				entityName = localName;
			else
				entityName = prefix + "_" + localName;

			switch (methodType) {

			case GET:
				createGetMethods();
				break;

			case SET:
				if (methodResource.isDatatypeProperty()) {
					// The property corresponding to the method is a datatype
					// property
					createSetMethodForDatatypeProperty();
				} else {
					// The property corresponding to the method is a object
					// property
					createSetMethodForObjectProperty();
				}
				break;

			case REMOVE_ALL:
				if (methodResource.isDatatypeProperty()) {
					// The property corresponding to the method is a datatype
					// property
					// createRemoveAllMethodForDatatypeProperty();
				} else {
					// The property corresponding to the method is a object
					// property
					// createRemoveAllMethodForObjectProperty();
				}
				break;

			default:
				break;
			}
		}
	}

	private void createGetMethods() {
		JType responseType = jCodeModel.ref(Response.class);
		String methodName = "getBy" + entityName.substring(0, 1).toUpperCase() + entityName.substring(1);
		JMethod temp = ((JDefinedClass) owner.asJDefinedClass()).getMethod(methodName, new JType[] { jCodeModel._ref(String.class) });
		if (temp == null) {
			jMethod = ((JDefinedClass) owner.asJDefinedClass()).method(JMod.PUBLIC, responseType, methodName);
			if (owner instanceof OntologyCodeClass) {
				createGetMethod();
				createGetEntityCentricMethod();
			}
		}
	}

	private void createGetEntityCentricMethod() {

		logger.debug("METHOD res " + this.ontResource + " OWNER res " + owner.getOntResource().getURI());

		OntologyCodeInterface javaInterfaceOwner = ontologyModel.getOntologyClass(owner.getOntResource(), BeanOntologyCodeInterface.class);
		String methodName = "get" + entityName.substring(0, 1).toUpperCase() + entityName.substring(1);
		JType responseType = jCodeModel.ref(Response.class);
		JMethod jMethod = ((JDefinedClass) owner.asJDefinedClass()).method(JMod.PUBLIC, responseType, methodName);

		jMethod.annotate(GET.class);
		jMethod.annotate(Path.class).param("value", "/entity/" + methodName);
		String operationId = "entity_" + ((RestOntologyCodeClass) owner).getPath().substring(1) + "_" + entityName;

		JVar idVar = jMethod.param(String.class, "id");
		idVar.annotate(ApiParam.class).param("value", "id").param("required", true);
		idVar.annotate(QueryParam.class).param("value", "id");

		JVar param = jMethod.param(String.class, "constraint");
		param.annotate(ApiParam.class).param("value", entityName).param("required", false);
		param.annotate(QueryParam.class).param("value", entityName);

		JBlock entityMethodBody = jMethod.body();

		JVar entityResponseBuilderVar = entityMethodBody.decl(jCodeModel._ref(ResponseBuilder.class), "_responseBuilder", JExpr._null());

		AbstractOntologyCodeClass beanOwner = ontologyModel.getOntologyClass(owner.getOntResource(), BeanOntologyCodeClass.class);
		AbstractOntologyCodeMethod getMethodBeanClass = beanOwner.getMethod(ontResource, methodType);
		AbstractOntologyCodeClass beanClassRange = getMethodBeanClass.getRange();
		AbstractOntologyCodeClass interfaceOwner = ontologyModel.getOntologyClass(owner.getOntResource(), BeanOntologyCodeInterface.class);

		jMethod.annotate(ApiOperation.class).param("value", "Get " + entityName + " values of this entity").param("nickname", operationId).param("response", beanClassRange.asJDefinedClass().dotclass()).param("responseContainer", "List");

		JType entityBeanSetType = null;
		JType entityBeanHashSetType = null;

		String getMethodName = "get" + entityName.substring(0, 1).toUpperCase() + entityName.substring(1);

		logger.debug("Searching for " + getMethodName + " on class " + beanOwner.getEntityName());

		JDefinedClass jdc = (JDefinedClass) beanOwner.asJDefinedClass();

		JMethod meth = jdc.getMethod(getMethodName, new JType[] {});

		JType methRetType = meth.type();

		JType methRetNarrowedType = methRetType.boxify().getTypeParameters().get(0);

		JType entitySetType = methRetType;

		entityBeanSetType = methRetType;
		entityBeanHashSetType = super.jCodeModel.ref(HashSet.class).narrow(methRetNarrowedType);

		JVar entityVar = entityMethodBody.decl(interfaceOwner.asJDefinedClass(), "_entity", interfaceOwner.asJDefinedClass().staticInvoke("get").arg(idVar));

		JVar entitykbSetVar = entityMethodBody.decl(entitySetType, "_kbSet", entityVar.invoke(getMethodName));
		JVar entityRetSetVar = entityMethodBody.decl(entityBeanSetType, "_retSet", JExpr._new(entityBeanHashSetType));

		JConditional entityIfBlock = entityMethodBody._if(entitykbSetVar.ne(JExpr._null()));

		/*
		 * Then
		 */
		JBlock entityIfThenBlock = entityIfBlock._then();
		JForEach entityForEach = entityIfThenBlock.forEach(methRetNarrowedType, "_obj", entitykbSetVar);

		JBlock entityForEachBlock = entityForEach.body();
		JExpression castExpression = JExpr.cast(ontologyModel.getOntologyClass(owner.getOntResource(), JenaOntologyCodeClass.class).asJDefinedClass(), entityForEach.var());
		if (super.ontResource.isDatatypeProperty()) {
			entityForEachBlock.add(entityRetSetVar.invoke("add").arg(entityForEach.var()));
		} else if (beanClassRange.getClass().equals(BooleanAnonClass.class)) {
			castExpression = JExpr.cast(beanClassRange.asJDefinedClass(), entityForEach.var());
			entityForEachBlock.add(entityRetSetVar.invoke("add").arg(castExpression.invoke("asMicroBean")));
		} else {
			castExpression = JExpr.cast(beanClassRange.asJDefinedClass(), entityForEach.var());
			entityForEachBlock.add(entityRetSetVar.invoke("add").arg(castExpression));
		}

		JExpression toListentity = entityRetSetVar.invoke("toArray").arg(JExpr.newArray(javaInterfaceOwner.asJDefinedClass(), entityRetSetVar.invoke("size")));

		entityIfThenBlock.assign(entityResponseBuilderVar, jCodeModel.ref(Response.class).staticInvoke("ok").arg(toListentity));

		/*
		 * Else
		 */
		JBlock entityIfElseBlock = entityIfBlock._else();
		entityIfElseBlock.assign(entityResponseBuilderVar, jCodeModel.ref(Response.class).staticInvoke("status").arg(jCodeModel.ref(Status.class).staticRef("NOT_FOUND")));

		entityMethodBody._return(entityResponseBuilderVar.invoke("build"));

	}

	private void createGetMethod() {

		BeanOntologyCodeInterface javaInterfaceOwner = ontologyModel.getOntologyClass(owner.getOntResource(), BeanOntologyCodeInterface.class);
		JType setType = jCodeModel.ref(Set.class).narrow(javaInterfaceOwner.asJDefinedClass());
		String methodName = "getBy" + entityName.substring(0, 1).toUpperCase() + entityName.substring(1);

		// Create annotation GET method
		jMethod.annotate(GET.class);
		jMethod.annotate(Path.class).param("value", "/" + methodName);
		String operationId = ((RestOntologyCodeClass) owner).getPath().substring(1) + "_" + methodName;
		jMethod.annotate(ApiOperation.class).param("value", "Get by " + entityName).param("nickname", operationId).param("response", javaInterfaceOwner.asJDefinedClass().dotclass()).param("responseContainer", "List");

		// GET Parameter
		JVar param = jMethod.param(String.class, "constraint");
		param.annotate(ApiParam.class).param("value", entityName).param("required", false);
		param.annotate(QueryParam.class).param("value", entityName);

		JBlock methodBody = jMethod.body();

		JVar responseBuilderVar = methodBody.decl(jCodeModel._ref(ResponseBuilder.class), "_responseBuilder", JExpr._null());
		JVar kbSetVar = methodBody.decl(setType, "_kbSet", JExpr._null());

		/* Check if constraint is null */
		JConditional ifConstraint = methodBody._if(param.ne(JExpr._null()));
		JBlock thenIfConstraint = ifConstraint._then();
		logger.debug("Method name: " + methodName + " OWNER: " + this.owner.getOntResource().getURI());
		// Restrict using constraint
		JVar obj = null;
		JType hashSetType = jCodeModel.ref(HashSet.class).narrow(javaInterfaceOwner.asJDefinedClass());
		if (ontResource.isObjectProperty() && !ontResource.isDatatypeProperty()) {
			AbstractOntologyCodeClass rangeJenaClass = ontologyModel.getOntologyClass(ModelFactory.createOntologyModel().getOntResource(OWL.Thing), JenaOntologyCodeClass.class);
			obj = thenIfConstraint.decl(jCodeModel._ref(LizardInterface.class), "obj", JExpr._new(rangeJenaClass.asJDefinedClass()).arg(jCodeModel.ref(ModelFactory.class).staticInvoke("createDefaultModel").invoke("getResource").arg(param)));
			JDefinedClass classHoldingGetByStaticMethod = null;
			JMethod methodToCall = null;
			if ((methodToCall = ((JDefinedClass) javaInterfaceOwner.asJDefinedClass()).getMethod(methodName, new JType[] { jCodeModel._ref(LizardInterface.class) })) == null) {
				logger.debug(javaInterfaceOwner.getEntityName() + " does not contain " + methodName + "(LizardInterface)");
				for (AbstractOntologyCodeClass superclass : javaInterfaceOwner.listSuperInterfaces()) {
					methodToCall = ((JDefinedClass) superclass.asJDefinedClass()).getMethod(methodName, new JType[] { jCodeModel._ref(LizardInterface.class) });
					classHoldingGetByStaticMethod = ((JDefinedClass) superclass.asJDefinedClass());
					if (methodToCall != null) {
						break;
					}
				}
				thenIfConstraint.assign(kbSetVar, JExpr._new(hashSetType));
				JForEach forEach = thenIfConstraint.forEach(methodToCall.type().boxify().getTypeParameters().iterator().next(), "i", classHoldingGetByStaticMethod.staticInvoke(methodToCall).arg(obj));
				forEach.body().add(kbSetVar.invoke("add").arg(javaInterfaceOwner.asJDefinedClass().staticInvoke("get").arg(forEach.var().invoke("getId"))));
			} else {
				thenIfConstraint.assign(kbSetVar, ((JDefinedClass) javaInterfaceOwner.asJDefinedClass()).staticInvoke(methodName).arg(obj));
			}

			// Constraint null
			JBlock elseBlock = ifConstraint._else();
			if (classHoldingGetByStaticMethod == null) {
				elseBlock.assign(kbSetVar, javaInterfaceOwner.asJDefinedClass().staticInvoke(methodName));
			} else {
				elseBlock.assign(kbSetVar, JExpr._new(hashSetType));
				JForEach forEach = elseBlock.forEach(methodToCall.type().boxify().getTypeParameters().iterator().next(), "i", classHoldingGetByStaticMethod.staticInvoke(methodToCall));
				forEach.body().add(kbSetVar.invoke("add").arg(javaInterfaceOwner.asJDefinedClass().staticInvoke("get").arg(forEach.var().invoke("getId"))));
			}
			/**/

		} else {
			// Get for datatytype

			Class<?> rangeClass = null;
			if (range == null) {
				rangeClass = String.class;
			} else if (OntologyProjectGenerationRecipe.hasTypeMapper(range.getOntResource().getURI())) {
				logger.trace("Range of the datatype property: " + this.ontResource.getURI() + " " + range.getOntResource().getURI());
				rangeClass = TypeMapper.getInstance().getTypeByName(range.getOntResource().getURI()).getJavaClass();
			} else {
				rangeClass = String.class;
			}

			JDefinedClass classHoldingGetByStaticMethod = null;
			JMethod methodToCall = null;
			if ((methodToCall = ((JDefinedClass) javaInterfaceOwner.asJDefinedClass()).getMethod(methodName, new JType[] { jCodeModel._ref(rangeClass) })) == null) {
				for (AbstractOntologyCodeClass superclass : javaInterfaceOwner.listSuperInterfaces()) {
					methodToCall = ((JDefinedClass) superclass.asJDefinedClass()).getMethod(methodName, new JType[] { jCodeModel._ref(rangeClass) });
					classHoldingGetByStaticMethod = ((JDefinedClass) superclass.asJDefinedClass());
					if (methodToCall != null) {
						break;
					}
				}
			} else {
				classHoldingGetByStaticMethod = (JDefinedClass) javaInterfaceOwner.asJDefinedClass();
			}

			{
				// Constraint not null
				JVar value = thenIfConstraint.decl(jCodeModel.ref(rangeClass), "datatypeValue", JExpr.cast(jCodeModel.ref(rangeClass), jCodeModel.ref(TypeMapper.class).staticInvoke("getInstance").invoke("getTypeByClass").arg(jCodeModel.ref(rangeClass).dotclass()).invoke("parse").arg(param)));
				thenIfConstraint.assign(kbSetVar, JExpr._new(hashSetType));
				JForEach forEach = thenIfConstraint.forEach(methodToCall.type().boxify().getTypeParameters().iterator().next(), "i", classHoldingGetByStaticMethod.staticInvoke(methodToCall).arg(value));
				forEach.body().add(kbSetVar.invoke("add").arg(javaInterfaceOwner.asJDefinedClass().staticInvoke("get").arg(forEach.var().invoke("getId"))));
			}

			{
				// Constraint null
				JBlock elseBlock = ifConstraint._else();
				elseBlock.assign(kbSetVar, JExpr._new(hashSetType));
				JForEach forEachElse = elseBlock.forEach(methodToCall.type().boxify().getTypeParameters().iterator().next(), "i", classHoldingGetByStaticMethod.staticInvoke(methodToCall));
				forEachElse.body().add(kbSetVar.invoke("add").arg(javaInterfaceOwner.asJDefinedClass().staticInvoke("get").arg(forEachElse.var().invoke("getId"))));
			}
		}

		JVar retSetVar = methodBody.decl(setType, "_retSet", JExpr._new(hashSetType));
		JConditional ifBlock = methodBody._if(kbSetVar.ne(JExpr._null()));
		/*
		 * Then
		 */
		JBlock ifThenBlock = ifBlock._then();
		JForEach forEach = ifThenBlock.forEach(javaInterfaceOwner.asJDefinedClass(), "_obj", kbSetVar);

		JBlock forEachBlock = forEach.body();
		JExpression castExpression = JExpr.cast(ontologyModel.getOntologyClass(owner.getOntResource(), JenaOntologyCodeClass.class).asJDefinedClass(), forEach.var());
		forEachBlock.add(retSetVar.invoke("add").arg(castExpression.invoke("asMicroBean")));

		JExpression toList = retSetVar.invoke("toArray").arg(JExpr.newArray(javaInterfaceOwner.asJDefinedClass(), retSetVar.invoke("size")));

		ifThenBlock.assign(responseBuilderVar, jCodeModel.ref(Response.class).staticInvoke("ok").arg(toList));

		/*
		 * Else
		 */
		JBlock ifElseBlock = ifBlock._else();
		ifElseBlock.assign(responseBuilderVar, jCodeModel.ref(Response.class).staticInvoke("status").arg(jCodeModel.ref(Status.class).staticRef("NOT_FOUND")));

		methodBody._return(responseBuilderVar.invoke("build"));

	}

	@SuppressWarnings("unused")
	private void createRemoveAllMethodForObjectProperty() {

		JType responseType = super.jCodeModel.ref(Response.class);
		String methodName = "removeAll" + entityName.substring(0, 1).toUpperCase() + entityName.substring(1);

		JMethod tempSet = ((JDefinedClass) owner.asJDefinedClass()).getMethod(methodName, new JType[] { super.jCodeModel._ref(String.class), super.jCodeModel._ref(String.class) });

		if (tempSet == null) {
			jMethod = ((JDefinedClass) owner.asJDefinedClass()).method(JMod.PUBLIC, responseType, methodName);
			if (owner instanceof OntologyCodeClass) {

				// Create annotation SET method
				jMethod.annotate(POST.class);
				jMethod.annotate(Path.class).param("value", "/entity/removeAll" + entityName.substring(0, 1).toUpperCase() + entityName.substring(1));
				String operationId = "removeAll_" + ((RestOntologyCodeClass) owner).getPath().substring(1) + "_" + entityName;
				jMethod.annotate(ApiOperation.class).param("value", "Delete " + entityName).param("nickname", operationId);

				// Create SET parameter
				// IRI of the target individual where the property will be added
				JVar idParam = jMethod.param(String.class, "id");
				idParam.annotate(ApiParam.class).param("value", "id").param("required", true);
				idParam.annotate(QueryParam.class).param("value", "id");

				JVar iriRangeParam = jMethod.param(String.class, "value");
				iriRangeParam.annotate(ApiParam.class).param("value", "value").param("required", true);
				iriRangeParam.annotate(QueryParam.class).param("value", "value");

				/*
				 * Method Body
				 */
				JBlock methodBody = jMethod.body();

				// Getting the target individual where the property will be
				// added
				AbstractOntologyCodeClass ownerInterface = ontologyModel.getOntologyClass(owner.getOntResource(), BeanOntologyCodeInterface.class);
				AbstractOntologyCodeClass rangeJenaClass = ontologyModel.getOntologyClass(domain.iterator().next().getOntResource(), JenaOntologyCodeClass.class);
				AbstractOntologyCodeClass rangeJenaInterface = ontologyModel.getOntologyClass(domain.iterator().next().getOntResource(), BeanOntologyCodeInterface.class);

				// if (range == null) {
				// rangeJenaClass = ontologyModel.getOntologyClass(ModelFactory.createOntologyModel().getOntResource(OWL.Thing), JenaOntologyCodeClass.class);
				// rangeJenaInterface = ontologyModel.getOntologyClass(ModelFactory.createOntologyModel().getOntResource(OWL.Thing), BeanOntologyCodeInterface.class);
				// } else {
				// rangeJenaClass = ontologyModel.getOntologyClass(range.getOntResource(), JenaOntologyCodeClass.class);
				// rangeJenaInterface = ontologyModel.getOntologyClass(range.getOntResource(), BeanOntologyCodeInterface.class);
				// if (rangeJenaClass == null) {
				// // The range is a boolean class
				// rangeJenaClass = ontologyModel.getOntologyClass(range.getOntResource(), BooleanAnonClass.class);
				// rangeJenaInterface = ontologyModel.getOntologyClass(range.getOntResource(), BooleanAnonClass.class);
				// }
				// }

				// Creting set to be added
				JType hashSetType_range = super.jCodeModel.ref(HashSet.class).narrow(rangeJenaInterface.asJDefinedClass());
				JType setType_range = super.jCodeModel.ref(Set.class).narrow(rangeJenaInterface.asJDefinedClass());
				JVar kbSetVar = methodBody.decl(setType_range, "toDelete", JExpr._new(hashSetType_range));
				methodBody.add(kbSetVar.invoke("add").arg(JExpr._new(rangeJenaClass.asJDefinedClass()).arg(jCodeModel.ref(ModelFactory.class).staticInvoke("createDefaultModel").invoke("createResource").arg(iriRangeParam))));

				// add set to the individual
				JVar entityVar = methodBody.decl(ownerInterface.asJDefinedClass(), "_entity", ownerInterface.asJDefinedClass().staticInvoke("get").arg(idParam));
				methodBody.add(entityVar.invoke(methodName).arg(kbSetVar));

				// Respond OK
				JVar responseBuilderVar = methodBody.decl(super.jCodeModel._ref(ResponseBuilder.class), "_responseBuilder", super.jCodeModel.ref(Response.class).staticInvoke("ok"));
				methodBody._return(responseBuilderVar.invoke("build"));
			}
		}

	}

	@SuppressWarnings("unused")
	private void createRemoveAllMethodForDatatypeProperty() {
		JType responseType = super.jCodeModel.ref(Response.class);
		String methodName = "removeAll" + entityName.substring(0, 1).toUpperCase() + entityName.substring(1);

		JMethod tempSet = ((JDefinedClass) owner.asJDefinedClass()).getMethod(methodName, new JType[] { super.jCodeModel._ref(String.class), super.jCodeModel._ref(String.class) });

		if (tempSet == null) {
			jMethod = ((JDefinedClass) owner.asJDefinedClass()).method(JMod.PUBLIC, responseType, methodName);
			if (owner instanceof OntologyCodeClass) {

				// Create annotation DELETE method
				jMethod.annotate(POST.class);
				jMethod.annotate(Path.class).param("value", "/entity/" + methodName);
				String operationId = "removeAll_" + ((RestOntologyCodeClass) owner).getPath().substring(1) + "_" + entityName;
				jMethod.annotate(ApiOperation.class).param("value", "Delete " + entityName).param("nickname", operationId);

				// Create SET parameter
				// IRI of the target individual where the property will be added
				JVar idParam = jMethod.param(String.class, "id");
				idParam.annotate(ApiParam.class).param("value", "id").param("required", true);
				idParam.annotate(QueryParam.class).param("value", "id");

				JVar rangeValueParam = jMethod.param(String.class, "value");
				rangeValueParam.annotate(ApiParam.class).param("value", "value").param("required", true);
				rangeValueParam.annotate(QueryParam.class).param("value", "value");

				/*
				 * Method Body
				 */
				JBlock methodBody = jMethod.body();
				AbstractOntologyCodeClass methodDomainClass = domain.iterator().next();
				AbstractOntologyCodeClass ownerInterface = ontologyModel.getOntologyClass(owner.getOntResource(), BeanOntologyCodeInterface.class);

				logger.debug("OWNER " + owner.getEntityName() + " " + this.ontResource.getURI() + " " + methodDomainClass.getOntResource().getURI());

				// Class<?> rangeClass = null;
				// if (methodDomainClass == null) {
				// rangeClass = String.class;
				// } else if (LizardCore.hasTypeMapper(range.getOntResource().getURI())) {
				// logger.trace("Range of the datatype property: " + this.ontResource.getURI() + " " + range.getOntResource().getURI());
				// rangeClass = TypeMapper.getInstance().getTypeByName(range.getOntResource().getURI()).getJavaClass();
				// } else {
				// rangeClass = String.class;
				// }

				JType hashSetType_range = super.jCodeModel.ref(HashSet.class).narrow(methodDomainClass.asJDefinedClass());
				JType setType_range = super.jCodeModel.ref(Set.class).narrow(methodDomainClass.asJDefinedClass());

				// JType setParameter = methodToCall.type();
				JVar kbSetVar = methodBody.decl(setType_range, "toDelete", JExpr._new(hashSetType_range));

				JVar value = methodBody.decl(methodDomainClass.asJDefinedClass(), "datatype", JExpr.cast(methodDomainClass.asJDefinedClass(), jCodeModel.ref(TypeMapper.class).staticInvoke("getInstance").invoke("getTypeByClass").arg(methodDomainClass.asJDefinedClass().dotclass()).invoke("parse").arg(rangeValueParam)));
				methodBody.add(kbSetVar.invoke("add").arg(value));

				// Add set to the individual
				JVar entityVar = methodBody.decl(ownerInterface.asJDefinedClass(), "_entity", ownerInterface.asJDefinedClass().staticInvoke("get").arg(idParam));
				methodBody.add(entityVar.invoke(methodName).arg(kbSetVar));

				// Respond OK
				JVar responseBuilderVar = methodBody.decl(super.jCodeModel._ref(ResponseBuilder.class), "_responseBuilder", super.jCodeModel.ref(Response.class).staticInvoke("ok"));
				methodBody._return(responseBuilderVar.invoke("build"));
			}
		}

	}

	private void createSetMethodForObjectProperty() {
		logger.debug("Create SET method for " + ontResource.getURI());
		logger.debug("DOMAIN NULL? " + (domain == null));
		logger.debug("RANGE NULL? " + (range == null));

		JType responseType = super.jCodeModel.ref(Response.class);
		String methodName = "set" + entityName.substring(0, 1).toUpperCase() + entityName.substring(1);

		JMethod tempSet = ((JDefinedClass) owner.asJDefinedClass()).getMethod(methodName, new JType[] { super.jCodeModel._ref(String.class), super.jCodeModel._ref(String.class) });

		if (tempSet == null) {
			jMethod = ((JDefinedClass) owner.asJDefinedClass()).method(JMod.PUBLIC, responseType, methodName);
			if (owner instanceof OntologyCodeClass) {

				// Create annotation SET method
				jMethod.annotate(POST.class);
				jMethod.annotate(Path.class).param("value", "/entity/set" + entityName.substring(0, 1).toUpperCase() + entityName.substring(1));
				String operationId = "set_" + ((RestOntologyCodeClass) owner).getPath().substring(1) + "_" + entityName;
				jMethod.annotate(ApiOperation.class).param("value", "Set " + entityName).param("nickname", operationId);

				// Create SET parameter
				// IRI of the target individual where the property will be added
				JVar idParam = jMethod.param(String.class, "id");
				idParam.annotate(ApiParam.class).param("value", "id").param("required", true);
				idParam.annotate(QueryParam.class).param("value", "id");

				JVar iriRangeParam = jMethod.param(String.class, "value");
				iriRangeParam.annotate(ApiParam.class).param("value", "value").param("required", true);
				iriRangeParam.annotate(QueryParam.class).param("value", "value");

				/*
				 * Method Body
				 */
				JBlock methodBody = jMethod.body();

				// Getting the target individual where the property will be
				// added
				AbstractOntologyCodeClass ownerInterface = ontologyModel.getOntologyClass(owner.getOntResource(), BeanOntologyCodeInterface.class);
//				AbstractOntologyCodeClass rangeJenaClass = ontologyModel.getOntologyClass(domain.iterator().next().getOntResource(), JenaOntologyCodeClass.class);
//				AbstractOntologyCodeClass rangeJenaInterface = ontologyModel.getOntologyClass(domain.iterator().next().getOntResource(), BeanOntologyCodeInterface.class);

				// if (range == null) {
				// rangeJenaClass = ontologyModel.getOntologyClass(ModelFactory.createOntologyModel().getOntResource(OWL.Thing), JenaOntologyCodeClass.class);
				// rangeJenaInterface = ontologyModel.getOntologyClass(ModelFactory.createOntologyModel().getOntResource(OWL.Thing), BeanOntologyCodeInterface.class);
				// } else {
				// rangeJenaClass = ontologyModel.getOntologyClass(range.getOntResource(), JenaOntologyCodeClass.class);
				// rangeJenaInterface = ontologyModel.getOntologyClass(range.getOntResource(), BeanOntologyCodeInterface.class);
				// if (rangeJenaClass == null) {
				// // The range is a boolean class
				// rangeJenaClass = ontologyModel.getOntologyClass(range.getOntResource(), BooleanAnonClass.class);
				// rangeJenaInterface = ontologyModel.getOntologyClass(range.getOntResource(), BooleanAnonClass.class);
				// }
				// }

				// Creting set to be added
				// JType hashSetType_range = super.jCodeModel.ref(HashSet.class).narrow(rangeJenaInterface.asJDefinedClass());
				// JType setType_range = super.jCodeModel.ref(Set.class).narrow(rangeJenaInterface.asJDefinedClass());
				// JVar kbSetVar = methodBody.decl(setType_range, "toAdd", JExpr._new(hashSetType_range));
				// methodBody.add(kbSetVar.invoke("add").arg(JExpr._new(rangeJenaClass.asJDefinedClass()).arg(jCodeModel.ref(ModelFactory.class).staticInvoke("createDefaultModel").invoke("createResource").arg(iriRangeParam))));
				//
				// // add set to the individual
				// JVar entityVar = methodBody.decl(ownerInterface.asJDefinedClass(), "_entity", ownerInterface.asJDefinedClass().staticInvoke("get").arg(idParam));
				// methodBody.add(entityVar.invoke(methodName).arg(kbSetVar));
				//
				// // create set response
				// JExpression cast = JExpr.cast(ontologyModel.getOntologyClass(owner.getOntResource(), JenaOntologyCodeClass.class).asJDefinedClass(), entityVar);
				JType hashSetType_range_res = super.jCodeModel.ref(HashSet.class).narrow(ownerInterface.asJDefinedClass());
				JType setType_range_res = super.jCodeModel.ref(Set.class).narrow(ownerInterface.asJDefinedClass());
				JVar kbSetVar_res = methodBody.decl(setType_range_res, "response", JExpr._new(hashSetType_range_res));
				// methodBody.add(kbSetVar_res.invoke("add").arg(cast.invoke("asMicroBean")));
				//
				// // Respond OK
				JVar responseBuilderVar = methodBody.decl(super.jCodeModel._ref(ResponseBuilder.class), "_responseBuilder", super.jCodeModel.ref(Response.class).staticInvoke("ok").arg(kbSetVar_res));
				methodBody._return(responseBuilderVar.invoke("build"));
			}
		}

	}

	private void createSetMethodForDatatypeProperty() {
		JType responseType = super.jCodeModel.ref(Response.class);
		String methodName = "set" + entityName.substring(0, 1).toUpperCase() + entityName.substring(1);

		JMethod tempSet = ((JDefinedClass) owner.asJDefinedClass()).getMethod(methodName, new JType[] { super.jCodeModel._ref(String.class), super.jCodeModel._ref(String.class) });

		if (tempSet == null) {
			jMethod = ((JDefinedClass) owner.asJDefinedClass()).method(JMod.PUBLIC, responseType, methodName);
			if (owner instanceof OntologyCodeClass) {

				// Create annotation SET method
				jMethod.annotate(POST.class);
				jMethod.annotate(Path.class).param("value", "/entity/set" + entityName.substring(0, 1).toUpperCase() + entityName.substring(1));
				String operationId = "set_" + ((RestOntologyCodeClass) owner).getPath().substring(1) + "_" + entityName;
				jMethod.annotate(ApiOperation.class).param("value", "Set " + entityName).param("nickname", operationId);

				// Create SET parameter
				// IRI of the target individual where the property will be added
				JVar idParam = jMethod.param(String.class, "id");
				idParam.annotate(ApiParam.class).param("value", "id").param("required", true);
				idParam.annotate(QueryParam.class).param("value", "id");

				JVar rangeValueParam = jMethod.param(String.class, "value");
				rangeValueParam.annotate(ApiParam.class).param("value", "value").param("required", true);
				rangeValueParam.annotate(QueryParam.class).param("value", "value");

				/*
				 * Method Body
				 */
				JBlock methodBody = jMethod.body();

				// Getting the target individual where the property will be
				// added
				logger.debug("OWNER " + owner.getEntityName() + ",ont resource: " + this.ontResource.getURI());
				AbstractOntologyCodeClass ownerInterface = ontologyModel.getOntologyClass(owner.getOntResource(), BeanOntologyCodeInterface.class);
				AbstractOntologyCodeClass methodDomainClass = domain.iterator().next();

				// Creting set to be added
				JType hashSetType_range = super.jCodeModel.ref(HashSet.class).narrow(methodDomainClass.asJDefinedClass());
				JType setType_range = super.jCodeModel.ref(Set.class).narrow(methodDomainClass.asJDefinedClass());
				JVar kbSetVar = methodBody.decl(setType_range, "toAdd", JExpr._new(hashSetType_range));

				RDFDatatype dClass = TypeMapper.getInstance().getTypeByName(methodDomainClass.getOntResource().getURI());

				if (dClass == null || dClass.getJavaClass().equals(String.class)) {
					methodBody.add(kbSetVar.invoke("add").arg(rangeValueParam));
				} else {
					JVar value = methodBody.decl(methodDomainClass.asJDefinedClass(), "datatype", JExpr.cast(methodDomainClass.asJDefinedClass(), jCodeModel.ref(TypeMapper.class).staticInvoke("getInstance").invoke("getTypeByClass").arg(methodDomainClass.asJDefinedClass().dotclass()).invoke("parse").arg(rangeValueParam)));
					methodBody.add(kbSetVar.invoke("add").arg(value));
				}

				// Add set to the individual
				JVar entityVar = methodBody.decl(ownerInterface.asJDefinedClass(), "_entity", ownerInterface.asJDefinedClass().staticInvoke("get").arg(idParam));
				methodBody.add(entityVar.invoke(methodName).arg(kbSetVar));

				// create set response
				JExpression cast = JExpr.cast(ontologyModel.getOntologyClass(owner.getOntResource(), JenaOntologyCodeClass.class).asJDefinedClass(), entityVar);
				JType hashSetType_range_res = super.jCodeModel.ref(HashSet.class).narrow(ownerInterface.asJDefinedClass());
				JType setType_range_res = super.jCodeModel.ref(Set.class).narrow(ownerInterface.asJDefinedClass());
				JVar kbSetVar_res = methodBody.decl(setType_range_res, "response", JExpr._new(hashSetType_range_res));
				methodBody.add(kbSetVar_res.invoke("add").arg(cast.invoke("asMicroBean")));

				// Respond OK
				JVar responseBuilderVar = methodBody.decl(super.jCodeModel._ref(ResponseBuilder.class), "_responseBuilder", super.jCodeModel.ref(Response.class).staticInvoke("ok").arg(kbSetVar_res));
				methodBody._return(responseBuilderVar.invoke("build"));
			}
		}

	}

	@Override
	public int hashCode() {
		return methodType.hashCode() + super.hashCode();
	}

}
