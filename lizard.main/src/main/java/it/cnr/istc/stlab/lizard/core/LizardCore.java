package it.cnr.istc.stlab.lizard.core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.ontology.OntTools;
import org.apache.jena.ontology.Ontology;
import org.apache.jena.ontology.Restriction;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDFS;

import com.sun.codemodel.CodeWriter;
import com.sun.codemodel.writer.FileCodeWriter;

import it.cnr.istc.stlab.lizard.commons.MavenUtils;
import it.cnr.istc.stlab.lizard.commons.OntologyCodeProject;
import it.cnr.istc.stlab.lizard.commons.exception.NotAvailableOntologyCodeEntityException;
import it.cnr.istc.stlab.lizard.commons.inmemory.RestInterface;
import it.cnr.istc.stlab.lizard.commons.model.AbstractOntologyCodeClass;
import it.cnr.istc.stlab.lizard.commons.model.AbstractOntologyCodeClassImpl;
import it.cnr.istc.stlab.lizard.commons.model.OntologyCodeClass;
import it.cnr.istc.stlab.lizard.commons.model.OntologyCodeInterface;
import it.cnr.istc.stlab.lizard.commons.model.OntologyCodeModel;
import it.cnr.istc.stlab.lizard.commons.model.anon.BooleanAnonClass;
import it.cnr.istc.stlab.lizard.commons.model.datatype.DatatypeCodeInterface;
import it.cnr.istc.stlab.lizard.commons.model.types.OntologyCodeMethodType;
import it.cnr.istc.stlab.lizard.commons.recipe.OntologyCodeGenerationRecipe;
import it.cnr.istc.stlab.lizard.core.model.BeanOntologyCodeClass;
import it.cnr.istc.stlab.lizard.core.model.BeanOntologyCodeInterface;
import it.cnr.istc.stlab.lizard.core.model.JenaOntologyCodeClass;
import it.cnr.istc.stlab.lizard.core.model.RestOntologyCodeClass;
import it.cnr.istc.stlab.lizard.core.model.RestOntologyCodeModel;

public class LizardCore implements OntologyCodeGenerationRecipe {

	private URI ontologyURI;
	private RestOntologyCodeModel restOntologyModel;
	private OntologyCodeModel ontologyModel;
	
	public LizardCore(URI ontologyURI) {
		this.ontologyURI = ontologyURI;
		
		OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        ontModel.read(ontologyURI.toString());
        
        this.ontologyModel = new RestOntologyCodeModel(ontModel);
    }
    
	@Override
    public OntologyCodeProject generate(){
		
		//OntologyCodeProject apiProject = new JenaLizard(ontologyURI).generate();
		OntologyCodeProject project = null;
		try {
			project = generateBeans();
		} catch (NotAvailableOntologyCodeEntityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		if(project != null){
			OntologyCodeModel beansModel = project.getOntologyCodeModel();
			project = generateRestProject(beansModel);
		}
		
		return project;
		
	}
	
	private OntologyCodeProject generateBeans() throws NotAvailableOntologyCodeEntityException{
	        
		OntologyCodeModel ontologyCodeModel = new RestOntologyCodeModel(this.ontologyModel.asOntModel());
		OntModel ontModel = ontologyCodeModel.asOntModel();
    	
    	String baseURI = ontModel.getNsPrefixURI("");
        if(baseURI == null){
        	ExtendedIterator<Ontology> ontologyIt = ontModel.listOntologies();
        	while(ontologyIt.hasNext()) baseURI = ontologyIt.next().getURI();
        	if(baseURI == null) ontModel.setNsPrefix("", ontologyURI.toString());
        	else ontModel.setNsPrefix("", baseURI);
        }
        
        URI ontologyBaseURI;
		try {
			ontologyBaseURI = new URI(baseURI);
		} catch (URISyntaxException e) {
			ontologyBaseURI = ontologyURI;
		}
        OntClass owlThing = ontModel.getOntClass(OWL2.Thing.getURI());
        
        /*
         * Create interface for owl:Thing
         */
        OntologyCodeInterface ontologyThingInterface = ontologyCodeModel.createOntologyClass(owlThing, BeanOntologyCodeInterface.class);
        createBeanMethods(ontologyThingInterface, ontologyCodeModel);
        
        /*
         * Create bean for owl:Thing
         */
        /*
         * Create java bean and Jena-based class.
         */
        ontologyCodeModel.createOntologyClass(owlThing, BeanOntologyCodeClass.class);
        ontologyCodeModel.createOntologyClass(owlThing, JenaOntologyCodeClass.class);
        
        //ontologyModel.createClassImplements(ontologyThingClass, ontologyThingInterface);
        
        List<OntClass> roots = OntTools.namedHierarchyRoots(ontModel);
        
        
        for(OntClass root : roots){
            visitHierarchyTreeForBeans(root, ontologyCodeModel);
        }
        
        /*
         * Create class implementations for java beans
         */
        Map<OntResource,BeanOntologyCodeClass> beanClassMap = ontologyCodeModel.getOntologyClasses(BeanOntologyCodeClass.class);
        Set<OntResource> ontResources = beanClassMap.keySet();
        final Set<AbstractOntologyCodeClassImpl> ontologyClasses = new HashSet<AbstractOntologyCodeClassImpl>();
        ontResources.forEach(ontResource -> {
        	if(ontResource.isURIResource()){
	        	BeanOntologyCodeClass ontologyClass = beanClassMap.get(ontResource);
	            ontologyClasses.add(ontologyClass);
        	}
        });
        
        ontologyClasses.forEach(ontologyClass -> {
        	
        	OntClass ontClass = (OntClass) ontologyClass.getOntResource();
        	OntologyCodeInterface ontologyInterface = ontologyCodeModel.getOntologyClass(ontClass, BeanOntologyCodeInterface.class);
        	
	        ExtendedIterator<OntClass> superClassIt = ontClass.listSuperClasses(false);
	        List<OntologyCodeInterface> ontologySuperInterfaces = new ArrayList<OntologyCodeInterface>();
	        ontologySuperInterfaces.add(ontologyCodeModel.getOntologyClass(ModelFactory.createOntologyModel().createOntResource(OWL2.Thing.getURI()), BeanOntologyCodeInterface.class));
	        
	        if(ontologyInterface != null)
	        	ontologySuperInterfaces.add(ontologyInterface);
	        
	        while(superClassIt.hasNext()){
	            OntClass superClass = superClassIt.next();
	            OntologyCodeInterface ontologySuperInterface = ontologyCodeModel.getOntologyClass(superClass, BeanOntologyCodeInterface.class);
	            if(ontologySuperInterface != null)
	                ontologySuperInterfaces.add(ontologySuperInterface);
	        }
	        OntologyCodeInterface[] classArray = new OntologyCodeInterface[ontologySuperInterfaces.size()];
	        
	        ontologyCodeModel.createClassImplements(ontologyClass, ontologySuperInterfaces.toArray(classArray));
	        
        });
        //addImplementations(ontologyClasses);
        
        /*
         * Create class implementations for Jena-based classes
         */
        Map<OntResource,JenaOntologyCodeClass> jenaClassMap = ontologyCodeModel.getOntologyClasses(JenaOntologyCodeClass.class);
        ontResources = jenaClassMap.keySet();
        final Set<AbstractOntologyCodeClassImpl> jenaClasses = new HashSet<AbstractOntologyCodeClassImpl>();
        for(OntResource ontResource : ontResources){
        	OntologyCodeClass ontologyClass = jenaClassMap.get(ontResource);
        	jenaClasses.add(ontologyClass);
        }
        
        jenaClasses.forEach(ontologyClass -> {
        	
        	OntClass ontClass = (OntClass) ontologyClass.getOntResource();
        	OntologyCodeInterface ontologyInterface = ontologyCodeModel.getOntologyClass(ontClass, BeanOntologyCodeInterface.class);
        	
	        ExtendedIterator<OntClass> superClassIt = ontClass.listSuperClasses(false);
	        List<OntologyCodeInterface> ontologySuperInterfaces = new ArrayList<OntologyCodeInterface>();
	        ontologySuperInterfaces.add(ontologyCodeModel.getOntologyClass(ModelFactory.createOntologyModel().createOntResource(OWL2.Thing.getURI()), BeanOntologyCodeInterface.class));
	        
	        if(ontologyInterface != null)
	        	ontologySuperInterfaces.add(ontologyInterface);
	        
	        while(superClassIt.hasNext()){
	            OntClass superClass = superClassIt.next();
	            OntologyCodeInterface ontologySuperInterface = ontologyCodeModel.getOntologyClass(superClass, BeanOntologyCodeInterface.class);
	            if(ontologySuperInterface != null)
	                ontologySuperInterfaces.add(ontologySuperInterface);
	        }
	        OntologyCodeInterface[] classArray = new OntologyCodeInterface[ontologySuperInterfaces.size()];
	        
	        ontologyCodeModel.createClassImplements(ontologyClass, ontologySuperInterfaces.toArray(classArray));
	        
        });
        
        //addImplementations(ontologyModel, ontologyClasses);
        
        
        return new OntologyCodeProject(ontologyBaseURI, ontologyCodeModel);
	}
	
	
	private OntologyCodeProject generateRestProject(OntologyCodeModel model){

		this.restOntologyModel = new RestOntologyCodeModel(model);
        
    	OntModel ontModel = restOntologyModel.asOntModel();
    	
    	String baseURI = ontModel.getNsPrefixURI("");
        if(baseURI == null){
        	ExtendedIterator<Ontology> ontologyIt = ontModel.listOntologies();
        	while(ontologyIt.hasNext()) baseURI = ontologyIt.next().getURI();
        	if(baseURI == null) ontModel.setNsPrefix("", ontologyURI.toString());
        	else ontModel.setNsPrefix("", baseURI);
        }
        
        URI ontologyBaseURI;
		try {
			ontologyBaseURI = new URI(baseURI);
		} catch (URISyntaxException e) {
			ontologyBaseURI = ontologyURI;
		}
        
        
        List<OntClass> roots = OntTools.namedHierarchyRoots(ontModel);
        
        
        for(OntClass root : roots){
        	visitHierarchyTreeForRest(root, restOntologyModel);
        }
        
        
        
         
                
        /*
        CodeWriter writer = new SingleStreamCodeWriter(System.out);
        
        
        try {
            codeModel.build(writer);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        */
        
        return new OntologyCodeProject(ontologyBaseURI, restOntologyModel);
    
	}
    
    private BooleanAnonClass manageAnonClasses(OntClass ontClass, OntologyCodeModel ontologyModel){
    	return ontologyModel.createAnonClass(ontClass);
    }
    
    private void visitHierarchyTreeForRest(OntClass ontClass, OntologyCodeModel ontologyModel){
        
    	
    	OntologyCodeClass ontologyClass;
		try {
			OntologyCodeClass bean = ontologyModel.getOntologyClass(ontClass, BeanOntologyCodeClass.class);
			if(ontologyModel.getOntologyClass(ontClass, BeanOntologyCodeClass.class) != null){
				ontologyClass = ontologyModel.createOntologyClass(ontClass, RestOntologyCodeClass.class);
				createMethods(ontologyClass, ontologyModel);
			}
		} catch (NotAvailableOntologyCodeEntityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
        
        ExtendedIterator<OntClass> subClasses = ontClass.listSubClasses();
        while(subClasses.hasNext()){
            OntClass subClass = subClasses.next();
            
            if(subClass.isURIResource()) visitHierarchyTreeForRest(subClass, ontologyModel);
            else manageAnonClasses(subClass, ontologyModel);
        }
        
    }
    
    private void visitHierarchyTreeForBeans(OntClass ontClass, OntologyCodeModel ontologyModel){
        
    	OntologyCodeInterface ontologyInterface = null;
		try {
			ontologyInterface = ontologyModel.createOntologyClass(ontClass, BeanOntologyCodeInterface.class);
		} catch (NotAvailableOntologyCodeEntityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(ontologyInterface != null){
	    	//ontologyInterface.createMethods();
	    	createBeanMethods(ontologyInterface, ontologyModel);
	    	
	    	
	        
	        //OntologyCodeClass ontologyClass = addClass(ontClass, ontologyModel);
	        try {
				ontologyModel.createOntologyClass(ontClass, BeanOntologyCodeClass.class);
				ontologyModel.createOntologyClass(ontClass, JenaOntologyCodeClass.class);
			} catch (NotAvailableOntologyCodeEntityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        
	        
	        
	        ExtendedIterator<OntClass> subClasses = ontClass.listSubClasses();
	        while(subClasses.hasNext()){
	            OntClass subClass = subClasses.next();
	            
	            if(subClass.isURIResource()) visitHierarchyTreeForBeans(subClass, ontologyModel);
	            else manageAnonClasses(subClass, ontologyModel);
	        }
		}
        
    }
    
    public void createServiceAnnotations(File root, OntologyCodeModel ontologyCodeModel){
    	Map<OntResource, RestOntologyCodeClass> restClassMap = ontologyCodeModel.getOntologyClasses(RestOntologyCodeClass.class);
    	Collection<RestOntologyCodeClass> restCalasses = restClassMap.values();
    	File metaInfFolder = new File(root, "src/main/resources/META-INF/services");
    	if(!metaInfFolder.exists()) metaInfFolder.mkdirs();
    	File restInterfaceAnnotation = new File(metaInfFolder, RestInterface.class.getCanonicalName());
    	System.out.println(getClass() + " created file " + restInterfaceAnnotation.getAbsolutePath());
    	BufferedWriter bw;
		try {
			bw = new BufferedWriter(new FileWriter(restInterfaceAnnotation));
			restCalasses.forEach(restClass -> {
				try {
					bw.write(restClass.asJDefinedClass().fullName());
					bw.newLine();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
	    	bw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    }
    
    private void createMethods(AbstractOntologyCodeClass owner, OntologyCodeModel ontologyModel){

    	OntClass ontClass = ontologyModel.asOntModel().getOntClass(owner.getOntResource().getURI());
        
        ExtendedIterator<OntProperty> propIt = ontClass.listDeclaredProperties();
        while(propIt.hasNext()){
            
            OntProperty ontProperty = propIt.next();
        
            OntResource range = ontProperty.getRange();
            
            if(range != null){
            	if(range.isURIResource()){
                    if(range.isClass()){
                    	
                    	
                    	OntologyCodeInterface rangeClass = null;
		            	/*
		            	 * The property is a datatype property.
		            	 * In this case we use Jena to map the range to the appropriate Java type. 
		            	 * E.g. xsd:string -> java.lang.String 
		            	 */
		            	OntClass rangeOntClass = ModelFactory.createOntologyModel().createClass(range.getURI());
		            	if(ontProperty.isDatatypeProperty()){
		            		try {
								rangeClass = ontologyModel.createOntologyClass(rangeOntClass, DatatypeCodeInterface.class);	
							} catch (NotAvailableOntologyCodeEntityException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
		            	}
		            	else{
		            		try {
								rangeClass = ontologyModel.createOntologyClass(rangeOntClass, BeanOntologyCodeInterface.class);
							} catch (NotAvailableOntologyCodeEntityException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
		            	}
		            	Collection<AbstractOntologyCodeClass> domain = new ArrayList<AbstractOntologyCodeClass>();
                        domain.add(rangeClass);
                        ontologyModel.createMethod(OntologyCodeMethodType.Get, ontProperty, owner, domain, rangeClass);
                        ontologyModel.createMethod(OntologyCodeMethodType.Set, ontProperty, owner, domain, rangeClass);
                    	
                    	
                    }
                }
            }
            else{
                OntResource thing = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM).createOntResource(OWL2.Thing.getURI());
                
                Collection<AbstractOntologyCodeClass> domain = new ArrayList<AbstractOntologyCodeClass>();
                domain.add(ontologyModel.getOntologyClass(thing, BeanOntologyCodeInterface.class));
                
                ontologyModel.createMethod(OntologyCodeMethodType.Get, ontProperty, owner, domain, ontologyModel.getOntologyClass(thing, RestOntologyCodeClass.class));
                ontologyModel.createMethod(OntologyCodeMethodType.Set, ontProperty, owner, domain, ontologyModel.getOntologyClass(thing, RestOntologyCodeClass.class));
            }
            
        }
        
        ExtendedIterator<OntClass> superClassesIt = ontClass.listSuperClasses();
        while(superClassesIt.hasNext()){
        	OntClass superClass = superClassesIt.next();
        	if(superClass.isRestriction()){
        		Restriction restriction = superClass.asRestriction();
        		OntProperty onProperty = restriction.getOnProperty();
        		Resource onClass = null;
        		if(restriction.isSomeValuesFromRestriction()){
        			onClass = restriction.asSomeValuesFromRestriction().getSomeValuesFrom();
        		}
        		else if(restriction.isAllValuesFromRestriction()){
        			onClass = restriction.asAllValuesFromRestriction().getAllValuesFrom();
        		}
        		/*
        		else if(restriction.isCardinalityRestriction()){
        			onClass = restriction.asCardinalityRestriction().get
        		}
        		*/
        		if(onClass != null){
        			
        			
        			try {
						OntologyCodeClass rangeClass = ontologyModel.createOntologyClass(ontologyModel.asOntModel().getOntResource(onClass), RestOntologyCodeClass.class);
						
						Collection<AbstractOntologyCodeClass> domain = new ArrayList<AbstractOntologyCodeClass>();
                        domain.add(rangeClass);
                        
                        ontologyModel.createMethod(OntologyCodeMethodType.Get, onProperty, owner, null, rangeClass);
						ontologyModel.createMethod(OntologyCodeMethodType.Set, onProperty, owner, null, rangeClass);
					} catch (NotAvailableOntologyCodeEntityException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
        				
        		}
        	}
        	
        }
        
    
    	
    }
    
    private void createBeanMethods(AbstractOntologyCodeClass owner, OntologyCodeModel ontologyModel){

        OntClass ontClass = ontologyModel.asOntModel().getOntClass(owner.getOntResource().getURI());
        
        ExtendedIterator<OntProperty> propIt = ontClass.listDeclaredProperties();
        while(propIt.hasNext()){
            
            OntProperty ontProperty = propIt.next();
        
            OntResource range = ontProperty.getRange();
            
            
            if(range != null){
            	
            	if(range.isURIResource()){
                    if(range.isClass()){
            	
                    	OntologyCodeInterface rangeClass = null;
		            	/*
		            	 * The property is a datatype property.
		            	 * In this case we use Jena to map the range to the appropriate Java type. 
		            	 * E.g. xsd:string -> java.lang.String 
		            	 */
		            	OntClass rangeOntClass = ModelFactory.createOntologyModel().createClass(range.getURI());
		            		
		            	if(ontProperty.isDatatypeProperty()){
		            		try {
								rangeClass = ontologyModel.createOntologyClass(rangeOntClass, DatatypeCodeInterface.class);
								
							} catch (NotAvailableOntologyCodeEntityException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
		            	}
		            	else{
		            		try {
								rangeClass = ontologyModel.createOntologyClass(rangeOntClass, BeanOntologyCodeInterface.class);
							} catch (NotAvailableOntologyCodeEntityException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
		            	}
		            	
		            	Collection<AbstractOntologyCodeClass> domain = new ArrayList<AbstractOntologyCodeClass>();
                        domain.add(rangeClass);
                        
                        ontologyModel.createMethod(OntologyCodeMethodType.Get, ontProperty, owner, null, rangeClass);
                        ontologyModel.createMethod(OntologyCodeMethodType.Set, ontProperty, owner, domain, null);
		            	
                    }
            	}
            }
            else{
                OntResource thing = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM).createOntResource(OWL2.Thing.getURI());
                
                OntologyCodeInterface rangeClass = null;
                /*
            	 * The property is a datatype property.
            	 * In this case we use Jena to map the range to the appropriate Java type. 
            	 * E.g. xsd:string -> java.lang.String 
            	 */
            	OntResource rangeOntClass = null;
            	
                if(ontProperty.isDatatypeProperty()){
            		try {
            			rangeOntClass = ModelFactory.createOntologyModel().createOntResource(RDFS.Literal.getURI());
						rangeClass = ontologyModel.createOntologyClass(rangeOntClass, DatatypeCodeInterface.class);
						
					} catch (NotAvailableOntologyCodeEntityException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
            	}
            	else{
            		try {
            			rangeOntClass = ModelFactory.createOntologyModel().createOntResource(OWL2.Thing.getURI());
						rangeClass = ontologyModel.createOntologyClass(rangeOntClass, BeanOntologyCodeInterface.class);
					} catch (NotAvailableOntologyCodeEntityException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
            	}
                
                Collection<AbstractOntologyCodeClass> domain = new ArrayList<AbstractOntologyCodeClass>();
                domain.add(rangeClass);
                
                ontologyModel.createMethod(OntologyCodeMethodType.Get, ontProperty, owner, null, rangeClass);
                ontologyModel.createMethod(OntologyCodeMethodType.Set, ontProperty, owner, domain, null);
            }
            
        }
        
        ExtendedIterator<OntClass> superClassesIt = ontClass.listSuperClasses();
        while(superClassesIt.hasNext()){
        	OntClass superClass = superClassesIt.next();
        	if(superClass.isRestriction()){
        		Restriction restriction = superClass.asRestriction();
        		OntProperty onProperty = restriction.getOnProperty();
        		Resource onClass = null;
        		if(restriction.isSomeValuesFromRestriction()){
        			onClass = restriction.asSomeValuesFromRestriction().getSomeValuesFrom();
        		}
        		else if(restriction.isAllValuesFromRestriction()){
        			onClass = restriction.asAllValuesFromRestriction().getAllValuesFrom();
        		}
        		/*
        		else if(restriction.isCardinalityRestriction()){
        			onClass = restriction.asCardinalityRestriction().get
        		}
        		*/
        		if(onClass != null){
        			
        			
        			try {
        				AbstractOntologyCodeClass rangeClass = ontologyModel.createOntologyClass(ontologyModel.asOntModel().getOntResource(onClass), BeanOntologyCodeInterface.class);
						
						Collection<AbstractOntologyCodeClass> domain = new ArrayList<AbstractOntologyCodeClass>();
                        domain.add(rangeClass);
						ontologyModel.createMethod(OntologyCodeMethodType.Get, onProperty, owner, null, rangeClass);
						ontologyModel.createMethod(OntologyCodeMethodType.Set, onProperty, owner, null, rangeClass);
					} catch (NotAvailableOntologyCodeEntityException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
        				
        		}
        	}
        	
        }
        
    
    	
    }
    
    public static void main(String[] args) {
        System.setProperty("M2_HOME", "/usr/local/apache-maven-3.1.1");
        System.setProperty("JAVA_HOME", "/Library/Java/JavaVirtualMachines/jdk1.8.0_66.jdk/Contents/Home");
        //codegen.generate();
        URI uri = null;
        try {
            //uri = new URI("http://www.ontologydesignpatterns.org/cp/owl/timeindexedsituation.owl");
            //uri = new URI("http://stlab.istc.cnr.it/documents/mibact/cultural-ON_xml.owl");
        	//uri = new URI("http://www.ontologydesignpatterns.org/ont/mario/tagging.owl");
        	uri = new URI("http://www.ontologydesignpatterns.org/ont/framester/framester.owl");
        	//uri = new URI("vocabs/foaf.rdf");
            
            OntologyCodeGenerationRecipe codegen = new LizardCore(uri);
            OntologyCodeProject ontologyCodeProject = codegen.generate();
            
            try {
            	File testFolder = new File("test_out");
            	if(testFolder.exists()) {
            		System.out.println("esists " + testFolder.getClass());
            		FileUtils.deleteDirectory(testFolder);
            	}
            	else System.out.println("not esists");
                File src = new File("test_out/src/main/java");
                File resources = new File("test_out/src/main/resources");
                File test = new File("test_out/src/test/java");
                if(!src.exists()) src.mkdirs();
                if(!resources.exists()) resources.mkdirs();
                if(!test.exists()) test.mkdirs();
                
                CodeWriter writer = new FileCodeWriter(src, "UTF-8");
                ontologyCodeProject.getOntologyCodeModel().asJCodeModel().build(writer);
                ((LizardCore)codegen).createServiceAnnotations(new File("test_out"), ontologyCodeProject.getOntologyCodeModel());
                
                /*
                 * Generate the POM descriptor file and build the project
                 * as a Maven project.
                 */
                File pom = new File("test_out/pom.xml");
                Writer pomWriter = new FileWriter(new File("test_out/pom.xml"));
                Map<String,String> dataModel = new HashMap<String,String>();
                dataModel.put("artifactId", ontologyCodeProject.getArtifactId());
                dataModel.put("groupId", ontologyCodeProject.getGroupId());
                MavenUtils.generatePOM(pomWriter, dataModel);
                MavenUtils.buildProject(pom);
                                
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } catch (URISyntaxException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }
    
}