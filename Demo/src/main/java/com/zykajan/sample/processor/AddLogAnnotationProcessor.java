package com.zykajan.sample.processor;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;

import java.util.Set;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.ElementScanner8;

import lombok.core.AST;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds System.out.println("...") statement to the beginning of annotated method via AST rewrite
 */
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("com.zykajan.sample.processor.LogEntrance")
public class AddLogAnnotationProcessor extends AbstractProcessor {

    private static final Logger log = LoggerFactory.getLogger(AddLogAnnotationProcessor.class);

    private Trees trees;
    private TreeMaker make;
    private Names names;
    private Messager messager;
    private JavacProcessingEnvironment javacProcessingEnv;
    private JavacElements utils;

    private JCTree.JCClassDecl classDecl;


    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        // Black magic starts here, supports only javac
        Context context = ((JavacProcessingEnvironment)
                processingEnv).getContext();
        this.javacProcessingEnv = (JavacProcessingEnvironment) processingEnv;
        messager = processingEnv.getMessager();
        this.trees = Trees.instance(processingEnv);
        this.make = TreeMaker.instance(context);
        this.names = Names.instance(context);

    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        log.info("Running processor {}", this);

        utils = javacProcessingEnv.getElementUtils();
        if (!roundEnv.processingOver()) {

            for (Element methodElement: roundEnv.getElementsAnnotatedWith(LogEntrance.class)) {
                log.info("Processing: {}", methodElement);
                JCTree classNode = utils.getTree(methodElement);
                classDecl = (JCTree.JCClassDecl) classNode;

                JCTree.JCPrimitiveTypeTree longType = make.TypeIdent(TypeTag.LONG);
                JCTree.JCLiteral arg = make.Literal(1L);
                // 生成private static final Logger log = LoggerFactory.getLogger(LoggerFactory.Type.SLF4J, <annotatedClass>, <system>, <module>);
                JCTree.JCExpression loggerType = make.Literal(LoggerFactory.class.getCanonicalName());
                JCTree.JCExpression getLoggerMethod = make.Literal(LoggerFactory.class.getCanonicalName() + ".getLogger");
                JCTree.JCExpression typeArg =  make.Literal(methodElement.getSimpleName() + ".class");

                JCTree.JCMethodInvocation getLoggerCall = make.Apply(List.nil(), getLoggerMethod, List.of(typeArg));

                JCTree.JCVariableDecl logField = make.VarDef(
                        make.Modifiers(Flags.PRIVATE | Flags.STATIC | Flags.FINAL),
                        names.fromString("log"), longType, arg);

                classDecl.defs = classDecl.defs.append(logField);
            }
        } else {
            log.info("Done");
        }

        return true;
    }
}
