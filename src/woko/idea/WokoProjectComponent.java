/*
 * Copyright 2001-2012 Remi Vankeisbelck
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package woko.idea;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import javax.swing.*;
import java.util.*;

public class WokoProjectComponent implements ProjectComponent {

    public static final Icon WOKO_ICON = IconLoader.findIcon("/woko/idea/woko.png");

    private final Project project;
    private GlobalSearchScope projectScope;
    private List<WideaFacetDescriptor> facetDescriptors = Collections.emptyList();

    private WokoToolWindow toolWindow = new WokoToolWindow();
    private List<String> facetPackages = null;

    public WokoProjectComponent(Project project) {
        this.project = project;
    }

    public void initComponent() {
    }

    public void disposeComponent() {
    }

    @NotNull
    public String getComponentName() {
        return "WokoProjectComponent";
    }

    private boolean isGroovy(PsiClass psiClass) {
        return psiClass != null && psiClass.getLanguage().getID().equals("Groovy");
    }


    public WokoToolWindow getToolWindow() {
        return toolWindow;
    }

    private void registerWokoToolWindow() {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow tw = toolWindowManager.registerToolWindow("Woko", true, ToolWindowAnchor.BOTTOM);
        WokoProjectComponent wpc = project.getComponent(WokoProjectComponent.class);
        WokoToolWindow wtw = wpc.getToolWindow();
        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        Content content = contentFactory.createContent(wtw.getMainPanel(), "", false);
        tw.getContentManager().addContent(content);
        tw.setIcon(WOKO_ICON);
    }

    private JavaPsiFacade getPsiFacade() {
        return JavaPsiFacade.getInstance(project);
    }

    public void projectOpened() {

        // register the tool window
        registerWokoToolWindow();

        projectScope = GlobalSearchScope.projectScope(project);
        // init tool window
        toolWindow.init(project);
    }

    public void projectClosed() {
        // called when project is being closed
        facetDescriptors = Collections.emptyList();

        // unregister the tool window
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        toolWindowManager.unregisterToolWindow("Woko");

    }

    public boolean openClassInEditor(String fqcn) {
        if (fqcn==null) {
            return false;
        }
        PsiClass c = getPsiClass(fqcn);
        if (c!=null) {
            c.getContainingFile().navigate(true);
            return true;
        }
        return false;
    }

    public List<WideaFacetDescriptor> getFacetDescriptors() {
        return facetDescriptors;
    }

    private void setStatusBarMessage(final String msg) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
                statusBar.setInfo(msg);
            }
        });
    }

    public void refresh() {
        setStatusBarMessage("Refreshing facets in the project...");
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir!=null) {
            if (facetPackages == null) {
                // grab packages from web.xml
                VirtualFile f = baseDir.findFileByRelativePath("src/main/webapp/WEB-INF/web.xml");
                List<String> pkgsFromConfig = new ArrayList<String>();
                if (f!=null) {
                    PsiFile file = PsiManager.getInstance(project).findFile(f);
                    if (file != null && file instanceof XmlFile) {
                        XmlFile xmlFile = (XmlFile)file;
                        XmlDocument doc = xmlFile.getDocument();
                        XmlTag[] tags = doc.getRootTag().getSubTags();
                        for (XmlTag tag : tags) {
                            if (tag.getName().equals("context-param")) {
                                String pName = tag.getSubTagText("param-name");
                                if (pName!=null && pName.equals("Woko.Facet.Packages")) {
                                    String packagesStr = tag.getSubTagText("param-value");
                                    if (packagesStr!=null) {
                                        pkgsFromConfig.addAll(extractPackagesList(packagesStr));
                                    }
                                }
                            }
                        }
                    }
                }
                if (pkgsFromConfig.size() == 0) {
                    toolWindow.balloonOnPackagesTextField("No packages found in web.xml !<br/> This can " +
                            " happen if you use a custom init, or <br/> if the project ain't even a Woko project !<br/>" +
                            "Add your facet package(s) to the list and refresh...");
                }
                // add default Woko packages
                pkgsFromConfig.add("facets");
                pkgsFromConfig.add("woko.facets.builtin");
                facetPackages = pkgsFromConfig;
            } else {
                // facet packages have been initialized : we need to
                // extract from the textField now !
                facetPackages = extractPackagesList(toolWindow.getFacetPackages());
            }

            // scan
            List<WideaFacetDescriptor> descriptors = new ArrayList<WideaFacetDescriptor>();
            Map<WideaFacetDescriptor,Long> refStamps = new HashMap<WideaFacetDescriptor,Long>();
            Map<String,WideaFacetDescriptor> filesDescriptors = new HashMap<String, WideaFacetDescriptor>();
            scanForFacets(facetPackages, descriptors, filesDescriptors, refStamps);

            // update fields
            facetDescriptors = descriptors;

        } else {
            facetDescriptors = Collections.emptyList();
        }
        // fire refresh for the tool window's table model
        toolWindow.refreshContents();
    }

    private void scanForFacets(
            List<String> packageNamesFromConfig,
            List<WideaFacetDescriptor> scannedDescriptors,
            Map<String,WideaFacetDescriptor> filesDescriptors,
            Map<WideaFacetDescriptor,Long> refStamps) {
        // scan configured package for classes annotated with @FacetKey[List]
        for (String pkgName : packageNamesFromConfig) {
            setStatusBarMessage("Woko plugin scanning package : " + pkgName);
            PsiPackage psiPkg = getPsiFacade().findPackage(pkgName);
            if (psiPkg!=null) {
                scanForFacetsRecursive(psiPkg, scannedDescriptors, filesDescriptors, refStamps);
            }
        }
        setStatusBarMessage("Woko plugin found " + scannedDescriptors.size() + " facets");
    }

    private void scanForFacetsRecursive(
            PsiPackage psiPkg,
            List<WideaFacetDescriptor> descriptors,
            Map<String,WideaFacetDescriptor> filesDescriptors,
            Map<WideaFacetDescriptor,Long> refStamps) {
        // scan classes in package
        PsiClass[] psiClasses = psiPkg.getClasses();
        for (PsiClass psiClass : psiClasses) {
            List<WideaFacetDescriptor> classDescriptors = getFacetDescriptorsForClass(psiClass);
            if (classDescriptors!=null) {
                // we need to check if a descriptor already exists in previously scanned
                // packages (re-implement JFacets' "first scanned wins" policy)
                for (WideaFacetDescriptor fd : classDescriptors) {
                    if (fd!=null && !descriptors.contains(fd)) {
                        descriptors.add(fd);
                        // set the files/descriptors entry and
                        // update refresh stamp : if there already is a stamp then
                        // keep it, otherwise grab the file's modif stamp
                        PsiFile containingFile = psiClass.getContainingFile();
                        VirtualFile vf = containingFile.getVirtualFile();
                        if (vf!=null) {
                            String absolutePath = vf.getPath();
                            filesDescriptors.put(absolutePath, fd);
                        }
                    }
                }
            }
        }
        // recurse in sub-packages
        PsiPackage[] subPackages = psiPkg.getSubPackages();
        for (PsiPackage subPackage : subPackages) {
            scanForFacetsRecursive(subPackage, descriptors, filesDescriptors, refStamps);
        }
    }

    private List<WideaFacetDescriptor> getFacetDescriptorsForClass(PsiClass psiFacetClass) {
        PsiModifierList modList = psiFacetClass.getModifierList();
        List<WideaFacetDescriptor> res = new ArrayList<WideaFacetDescriptor>();
        if (modList!=null) {
            PsiAnnotation psiFacetKey = getAnnotation(psiFacetClass, "net.sourceforge.jfacets.annotations.FacetKey");
            if (psiFacetKey!=null) {
                res.add(createDescriptorForKey(psiFacetClass, psiFacetKey));
            } else {
                PsiAnnotation psiFacetKeyList = getAnnotation(psiFacetClass, "net.sourceforge.jfacets.annotations.FacetKeyList");
                if (psiFacetKeyList!=null) {
                    res.addAll(createDescriptorsForKeyList(psiFacetClass, psiFacetKeyList));
                }
            }
        }
        return res;
    }

    private List<WideaFacetDescriptor> createDescriptorsForKeyList(PsiClass psiFacetClass, PsiAnnotation psiFacetKeyList) {
        PsiNameValuePair[] nvps = psiFacetKeyList.getParameterList().getAttributes();
        List<WideaFacetDescriptor> res = new ArrayList<WideaFacetDescriptor>();
        if (nvps.length==1) {
            PsiNameValuePair nvp = nvps[0];
            String name = nvp.getName();
            if (name!=null && name.equals("keys")) {
                PsiAnnotationMemberValue mv = nvp.getValue();
                if (mv instanceof PsiArrayInitializerMemberValue) {
                    PsiArrayInitializerMemberValue v = (PsiArrayInitializerMemberValue)nvp.getValue();
                    if (v!=null) {
                        PsiAnnotationMemberValue[] keys = v.getInitializers();
                        for (PsiAnnotationMemberValue key : keys) {
                            PsiAnnotation a = (PsiAnnotation)key;
                            res.add(createDescriptorForKey(psiFacetClass, a));
                        }
                    }
                } else if (mv!=null) {
                    PsiElement[] children = mv.getChildren();
                    for (PsiElement child : children) {
                        if (child instanceof PsiAnnotation) {
                            res.add(createDescriptorForKey(psiFacetClass, (PsiAnnotation)child));
                        }
                    }
                }
            }
        }
        return res;
    }

    private String unquote(String text) {
        return text!=null ? text.replace("\"", "") : null;
    }

    private String getValueFromResolveResult(ResolveResult rr) {
        PsiElement elem = rr.getElement();
        if (elem instanceof PsiField) {
            PsiField pf = (PsiField)elem;
            PsiExpression initializer = pf.getInitializer();
            if (initializer!=null) {
                return unquote(initializer.getText());
            }
        }
        return null;
    }

    private String getNvpValueAsText(PsiAnnotationMemberValue pv) {
        if (pv!=null) {
            if (pv instanceof GrReferenceElement<?>) {
                GrReferenceElement<?> re = (GrReferenceElement<?>)pv;
                GroovyResolveResult rr = re.advancedResolve();
                return getValueFromResolveResult(rr);
            } else if (pv instanceof PsiReferenceExpression) {
                PsiReferenceExpression re = (PsiReferenceExpression)pv;
                JavaResolveResult rr = re.advancedResolve(true);
                return getValueFromResolveResult(rr);
            } else {
                return unquote(pv.getText());
            }
        }
        return null;
    }

    private WideaFacetDescriptor createDescriptorForKey(PsiClass psiFacetClass, PsiAnnotation psiFacetKey) {
        String name = getNvpValueAsText(psiFacetKey.findAttributeValue("name"));
        String profileId = getNvpValueAsText(psiFacetKey.findAttributeValue("profileId"));

        String targetObjectType = null;
        PsiAnnotationMemberValue pv = psiFacetKey.findAttributeValue("targetObjectType");
        PsiType classType = null;
        if (pv instanceof PsiClassObjectAccessExpression) {
            PsiClassObjectAccessExpression cae = (PsiClassObjectAccessExpression)pv;
            classType = cae.getType();
        } else if (pv instanceof GrReferenceExpression) {
            GrReferenceExpression refExpr = (GrReferenceExpression)pv;
            classType = refExpr.getNominalType();
        } else if (pv instanceof PsiLiteralExpression) {
            // don't know why, but in some situations
            // we get that type of values, which are always null...
            // happens systematically with code found in dependencies
            targetObjectType = "UNSUPPORTED YET!";
        }
        if (classType instanceof PsiImmediateClassType) {
            PsiImmediateClassType ict = (PsiImmediateClassType)classType;
            PsiType[] parameters = ict.getParameters();
            if (parameters.length==1) {
                targetObjectType = parameters[0].getCanonicalText();
            }
        }

        String facetClassName = psiFacetClass.getQualifiedName();
        targetObjectType = targetObjectType==null ? "java.lang.Object" : targetObjectType;
        if (name!=null && profileId!=null && targetObjectType!=null && facetClassName!=null) {
            FdType type = null;
            PsiClass psiClass = getPsiClass(facetClassName);
            if (psiClass==null) {
                type = FdType.Compiled;
            } else {
                type = isGroovy(psiClass) ? FdType.Groovy : FdType.Java;
            }
            return new WideaFacetDescriptor(name, profileId, targetObjectType, facetClassName, type);
        }
        return null;
    }

    public PsiAnnotation getAnnotation(PsiClass psiClass, String annotFqcn) {
        PsiModifierList modifierList = psiClass.getModifierList();
        if (modifierList==null) {
            return null;
        }
        PsiAnnotation[] annots = modifierList.getAnnotations();
        for (PsiAnnotation a : annots) {
            String qn = a.getQualifiedName();
            if (qn!=null) {
                if (qn.equals(annotFqcn)) {
                    return a;
                }
            }
        }
        return null;
    }

    public PsiClass getPsiClass(String fqcn) {
        try {
            return getPsiFacade().findClass(fqcn, projectScope);
        } catch(Exception e) {
            return null;
        }
    }

    public PsiFile getPsiFile(String fqcn) {
        PsiClass pc = getPsiClass(fqcn);
        if (pc!=null) {
            return pc.getContainingFile();
        }
        return null;
    }

    public static List<String> extractPackagesList(String packagesStr) {
        String[] pkgNamesArr = packagesStr.
                replace('\n', ',').
                replace(' ', ',').
                split(",");
        List<String> pkgNames = new ArrayList<String>();
        for (String s : pkgNamesArr) {
            if (s != null && !s.equals("")) {
                pkgNames.add(s);
            }
        }
        return pkgNames;
    }

    public List<String> getFacetPackages() {
        return facetPackages;
    }
}

