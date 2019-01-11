package jp.mufg.chronicle.queue.testclasses;

import net.openhft.lang.model.constraints.MaxSize;

/**
 * Created by daniels on 26/02/2015.
 */
public interface ObjectWithoutEnumDataValueClass
{
    String getSomeString();

    void setSomeString(@MaxSize(64) String someString);

    double getSomeDouble();

    void setSomeDouble(double someDouble);

    double getSomeInt();

    void setSomeInt(double someInt);
}