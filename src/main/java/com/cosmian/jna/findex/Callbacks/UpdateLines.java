package com.cosmian.jna.findex.Callbacks;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cosmian.CloudproofException;
import com.cosmian.jna.findex.FindexWrapper.UpdateLinesCallback;
import com.cosmian.jna.findex.FindexWrapper.UpdateLinesInterface;
import com.cosmian.jna.findex.Leb128Serializer;
import com.cosmian.jna.findex.Uid;
import com.sun.jna.Pointer;

public class UpdateLines implements UpdateLinesCallback {

    private UpdateLinesInterface update;

    public UpdateLines(UpdateLinesInterface update) {
        this.update = update;
    }

    @Override
    public int apply(Pointer removedChainsPointer,
                     int removedChainsLength,
                     Pointer newEntriesPointer,
                     int newEntriesLength,
                     Pointer newChainsPointer,
                     int newChainsLength)
        throws CloudproofException {

        byte[] removedChainsBytes = new byte[removedChainsLength];
        removedChainsPointer.read(0, removedChainsBytes, 0, removedChainsLength);
        List<Uid> removedChains = Leb128Serializer.deserializeList(removedChainsBytes).stream().map(b -> new Uid(b))
            .collect(Collectors.toList());
        ;

        byte[] newEntriesBytes = new byte[newEntriesLength];
        newEntriesPointer.read(0, newEntriesBytes, 0, newEntriesLength);
        Map<Uid, byte[]> newEntries = Leb128Serializer.deserializeHashmap(newEntriesBytes);

        byte[] newChainsBytes = new byte[newChainsLength];
        newChainsPointer.read(0, newChainsBytes, 0, newChainsLength);
        Map<Uid, byte[]> newChains = Leb128Serializer.deserializeHashmap(newChainsBytes);

        this.update.update(removedChains, newEntries, newChains);
        return 0;
    }

}
