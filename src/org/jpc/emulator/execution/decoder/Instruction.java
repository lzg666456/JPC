package org.jpc.emulator.execution.decoder;

import java.util.*;

public class Instruction
{
    private static Set<String> invalid = new HashSet();
    private static Set<String> call = new HashSet();
    private static Set<String> ret = new HashSet();
    private static Set<String> jmp = new HashSet();
    private static Set<String> jcc = new HashSet();
    private static Set<String> hlt = new HashSet();

    //public static enum Branch {None, T1, T2, Jmp_Unknown, Call, Call_Unknown, Ret, Exception};

    static
    {
        invalid.add("invalid");
        call.add("call");
        call.add("syscall");
        call.add("vmcall");
        call.add("vmmcall");
        ret.add("ret");
        ret.add("retf");
        ret.add("sysret");
        ret.add("iretw");
        ret.add("iretd");
        ret.add("iretq");
        jmp.add("jmp");
        jcc.add("jo");
        jcc.add("jno");
        jcc.add("jb");
        jcc.add("jbe");
        jcc.add("ja");
        jcc.add("jae");
        jcc.add("je");
        jcc.add("jne");
        jcc.add("js");
        jcc.add("jns");
        jcc.add("jp");
        jcc.add("jnp");
        jcc.add("jl");
        jcc.add("jle");
        jcc.add("jg");
        jcc.add("jge");
        jcc.add("jcxz");
        jcc.add("jecxz");
        jcc.add("jrcxz");
        jcc.add("loop");
        jcc.add("loope");
        jcc.add("loopnz");
        hlt.add("hlt");
    }

    public int x86Length;
    public long eip;
    ZygoteInstruction zygote;
    public String operator = "invalid";
    public Operand[] operand = new Operand[0];
    public Prefix pfx = new Prefix();
    int opr_mode, adr_mode;
    String branch_dist;
    public Instruction next;
    
    public Instruction() {}

    public String getGeneralClassName()
    {
        StringBuffer b = new StringBuffer();
        if (pfx.rep != 0)
            b.append("rep_");
        if (pfx.repne != 0)
            b.append("repne_");
        b.append(operator);
        for (int i=0; i < zygote.operand.length; i++)
            if (!zygote.operand[i].name.equals("NONE"))
                b.append("_"+zygote.operand[i].name);
        boolean mem = false;
        for (Operand o : operand)
            if (o.type.equals("OP_MEM"))
                mem = true;
        if (mem)
            b.append("_mem");
        return b.toString();
    }

    public String getSpecificClassName()
    {
        StringBuffer b = new StringBuffer();
        if (pfx.rep != 0)
            b.append("rep_");
        if (pfx.repne != 0)
            b.append("repne_");
        b.append(operator);
        b.append("_o"+opr_mode);
        for (int i=0; i < zygote.operand.length; i++)
            if (!zygote.operand[i].name.equals("NONE"))
                b.append("_"+zygote.operand[i].name);
        boolean mem = false;
        for (Operand o : operand)
            if (o.type.equals("OP_MEM"))
                mem = true;
        if (mem)
            b.append("_mem");
        return b.toString();
    }

    public void configure(Instruction parent)
    {}

    public int opSize(int arg)
    {
        return operand[arg].size;
    }

    public static class Ptr
    {
        public int off, seg;

        public Ptr(int off, int seg)
        {
            this.off = off;
            this.seg = seg;
        }
    }

    public static class Prefix
    {
        public int rex, opr, adr, lock, rep, repe, repne, insn;
        public String seg;
        
        public String toString()
        {
            StringBuffer b = new StringBuffer();
            if (lock != 0)
                b.append("lock ");
            if (rep != 0)
                b.append("rep ");
            //if (repe != 0)
            //    b.append("repe ");
            if (repne != 0)
                b.append("repne ");
            //if (seg != null)
            //    b.append(seg+" ");
            
            return b.toString();
        }
    }

    public void cast()
    {
        for (Operand op: operand)
            op.cast();
    }

    public static class Operand
    {
        private int x86Length;
        public Instruction parent;
        long eip;
        public String seg;
        public String type;
        public String base;
        public int size;
        public long lval, offset;
        public Ptr ptr; // should be same as lval somehow
        public String index;
        public long scale;
        int cast;
        // required for patterns
        int maxSize;
        int imm_start, dis_start;

        public void cast()
        {
           if (type.equals("OP_MEM"))
           {
               if (offset ==8)
                   lval = (byte) lval;
               else if (offset == 16)
                   lval = (short) lval;
               else if (offset == 32)
                   lval = (int) lval;
           } 
           else if (type.equals("OP_IMM") || type.equals("OP_JIMM"))
           {
               if (size ==8)
                   lval = (byte) lval;
               else if (size == 16)
                   lval = (short) lval;
               else if (size == 32)
                   lval = (int) lval;
           }
        }

        public String toString()
        {
            return toString(false);
        }

        public String toString(boolean pattern)
        {
            if (type == null)
                return "UNKNOWN - AVX?";
            if (type.equals("OP_REG"))
                return base;
            boolean first = true;
            StringBuffer b = new StringBuffer();
            //if (cast == 1)
            //    b.append(intel_size(size));
            if (type.equals("OP_MEM"))
            {
                b.append(intel_size(size));                    
                
                if (seg != null)
                    b.append(seg + ":");
                else if ((base == null) && (index == null))
                    b.append("ds:");
                if ((base != null) || (index != null))
                    b.append("[");

                if (base != null)
                {
                    b.append(base);
                    first = false;
                }
                if (index != null)
                {
                    if (!first)
                        b.append("+");
                    b.append(index);
                    first = false;
                }
                if (scale != 0)
                    b.append("*"+scale);
                if ((offset == 8) || (offset == 16) || (offset == 32) || (offset == 64))
                {
                    if (!pattern)
                    {
                        if ((lval <0) && ((base != null) || (index != null)))
                            b.append("-"+String.format("0x%x", -lval));
                        else
                        {
                            if (!first)
                                b.append("+"+String.format("0x%x", lval & ((1L << offset)-1)));
                            else
                                b.append(String.format("0x%x", lval & ((1L << offset)-1)));
                        }
                    }
                    else
                    {
                        b.append("$");
                        for (int i=0; i < offset/8; i++)
                            b.append("D");
                        
                    }
                }
                if ((base != null) || (index != null))
                    b.append("]");
            }
            else if (type.equals("OP_IMM"))
            {
                if (!pattern)
                {
                    if (lval <0)
                    {
                        if (sign_extends.contains(parent.operator)) // these are sign extended
                            b.append(String.format("0x%x", lval & ((1L << maxSize)-1)));
                        else
                            b.append(String.format("0x%x", lval & ((1L << size)-1)));
                    }
                    else
                        b.append(String.format("0x%x", lval));
                }
                else
                {
                    b.append("$");
                    //if (sign_extends.contains(parent.operator)) // these are sign extended
                    //    for (int i=0; i < maxSize/8; i++)
                    //        b.append("I");
                    //else
                        for (int i=0; i < size/8; i++)
                            b.append("I");
                        if (size == 0) // shr etc.
                            b.append("I"); 
                }
            }
            else if (type.equals("OP_JIMM"))
            {
                if (!pattern)
                {
                    if (eip+x86Length+lval < 0)
                        b.append(String.format("0x%x", (eip+x86Length+lval) & ((1L << maxSize)-1)));
                    else
                        b.append(String.format("0x%x", eip+x86Length+lval));
                }
                else
                {
                    b.append("$");
                    for (int i=0; i < size/8; i++)
                        b.append("I");
                }
            }
            else if (type.equals("OP_PTR"))
            {
                if (!pattern)
                    b.append(String.format("0x%x:0x%x", ptr.seg & 0xFFFF, ptr.off));
                else
                    b.append("$SSSS:$DDDDDDDD");
            }       
            return b.toString();
            //return String.format("[%s %s %s %d %x %x %x]", type, base, index, size, lval, offset, scale);
        }
    }
    
    private static List<String> sign_extends = Arrays.asList("cmp", "or", "imul", "adc", "add", "sbb", "xor", "push", "and");

    private static String intel_size(int size)
    {
        switch (size)
        {
        case 0:
            return "";//XMMWORD PTR ";
        case 8:
            return "BYTE PTR ";
        case 16:
            return "WORD PTR ";
        case 32:
            return "DWORD PTR ";
        case 64:
            return "QWORD PTR ";
        case 80:
            return "TBYTE PTR ";
        default:
            throw new IllegalStateException("Unknown operand size " + size);
        }
    }

    private static boolean print_size = false;

    public String getPattern()
    {
        return toString(true);
    }

    public String toString()
    {
        return toString(false);
    }

    public String toString(boolean pattern)
    {
        int maxSize = 0;
        for (Operand op: operand)
        {
            op.parent = this;
            if (op.size > maxSize)
                maxSize = op.size;
            op.eip = eip;
            op.x86Length = x86Length;
        }
        for (Operand op: operand)
            op.maxSize = maxSize;

        if (print_size)
        {
            if (operand.length == 1)
                return String.format("(%d bytes) %s%s %s", x86Length, pfx, operator + (branch_dist == null ? "": " "+branch_dist), operand[0].toString(pattern));
            else if (operand.length == 2)
                return String.format("(%d bytes) %s%s %s, %s", x86Length, pfx, operator + (branch_dist == null ? "": " "+branch_dist), operand[0].toString(pattern), operand[1].toString(pattern));
            else if (operand.length == 3)
                return String.format("(%d bytes) %s%s %s, %s, %s", x86Length, pfx, operator + (branch_dist == null ? "": " "+branch_dist), operand[0].toString(pattern), operand[1].toString(pattern), operand[2].toString(pattern));
            return String.format("(%d bytes) %s%s", x86Length, pfx, operator + (branch_dist == null ? "": " "+branch_dist));
        }
        if (operand.length == 1)
            return String.format("%s%s %s", pfx, operator /*+ (branch_dist == null ? "": " "+branch_dist)*/, operand[0].toString(pattern));
        else if (operand.length == 2)
            return String.format("%s%s %s,%s", pfx, operator /*+ (branch_dist == null ? "": " "+branch_dist)*/, operand[0].toString(pattern), operand[1].toString(pattern));
        else if (operand.length == 3)
            return String.format("%s%s %s,%s,%s", pfx, operator /*+ (branch_dist == null ? "": " "+branch_dist)*/, operand[0].toString(pattern), operand[1].toString(pattern), operand[2].toString(pattern));
        return String.format("%s%s", pfx, operator /*+ (branch_dist == null ? "": " "+branch_dist)*/);
    }

    public boolean isBranch()
    {
        return jcc.contains(operator) || jmp.contains(operator) || call.contains(operator) || ret.contains(operator) || hlt.contains(operator) || usesControlReg(0);
    }

    public boolean usesControlReg(int op)
    {
        return (zygote.operand[op].name.equals("C"));
    }

    public boolean isJcc()
    {
        return jcc.contains(operator);
    }

    public boolean isJmp()
    {
        return jmp.contains(operator);
    }

    public boolean isCall()
    {
        return call.contains(operator);
    }
    
    public boolean isRet()
    {
        return ret.contains(operator);
    }

    public boolean isHalt()
    {
        return hlt.contains(operator);
    }
}