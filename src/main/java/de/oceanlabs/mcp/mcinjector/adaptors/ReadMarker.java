package de.oceanlabs.mcp.mcinjector.adaptors;

import java.util.logging.Logger;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import de.oceanlabs.mcp.mcinjector.MCInjectorImpl;

public class ReadMarker extends ClassVisitor
{
    private static final Logger log = Logger.getLogger("MCInjector");
    private String className;

    public ReadMarker(ClassVisitor cv, MCInjectorImpl mci)
    {
        super(Opcodes.ASM5, cv);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
    {
        this.className = name;;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value)
    {
        if (name.equals("__OBFID"))
        {
            log.info(" MarkerID: " + String.valueOf(value) + " " + className);
        }
        return super.visitField(access, name, desc, signature, value);
    }
}
