package org.jpc.emulator.execution.opcodes.pm;

import org.jpc.emulator.execution.*;
import org.jpc.emulator.execution.decoder.*;
import org.jpc.emulator.processor.*;
import org.jpc.emulator.processor.fpu64.*;
import static org.jpc.emulator.processor.Processor.*;

public class outsb_a32 extends Executable
{
    final int segIndex;

    public outsb_a32(int blockStart, int eip, int prefices, PeekableInputStream input)
    {
        super(blockStart, eip);
        segIndex = Modrm.getSegmentIndex(prefices);
    }

    public Branch execute(Processor cpu)
    {
        Segment seg = cpu.segs[segIndex];
        if (cpu.checkIOPermissions8(cpu.r_dx.get16() & 0xffff))
            StaticOpcodes.outsb_a32(cpu, seg);
        else
            throw ProcessorException.GENERAL_PROTECTION_0;
        return Branch.None;
    }

    public boolean isBranch()
    {
        return false;
    }

    public String toString()
    {
        return this.getClass().getName();
    }
}