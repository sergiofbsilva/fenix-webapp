package pt.ist.fenix.task;

import java.math.BigDecimal;

import org.fenixedu.academic.domain.accounting.InterestRate;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.joda.time.LocalDate;

/**
 * Created by Sérgio Silva (hello@fenixedu.org).
 */
public class BootstrapInterestRatesTask extends CustomTask{

    @Override
    public void runTask() throws Exception {
        InterestRate.getOrCreate(new LocalDate(2011, 1, 1), new LocalDate(2011, 12, 31), BigDecimal.valueOf(6.351));
        InterestRate.getOrCreate(new LocalDate(2012, 1, 1), new LocalDate(2012, 12, 31), BigDecimal.valueOf(7.007));
        InterestRate.getOrCreate(new LocalDate(2013, 1, 1), new LocalDate(2013, 12, 31), BigDecimal.valueOf(6.112));
        InterestRate.getOrCreate(new LocalDate(2014, 1, 1), new LocalDate(2014, 12, 31), BigDecimal.valueOf(5.535));
        InterestRate.getOrCreate(new LocalDate(2015, 1, 1), new LocalDate(2015, 12, 31), BigDecimal.valueOf(5.476));
        InterestRate.getOrCreate(new LocalDate(2016, 1, 1), new LocalDate(2016, 12, 31), BigDecimal.valueOf(5.168));
        InterestRate.getOrCreate(new LocalDate(2017, 1, 1), new LocalDate(2017, 12, 31), BigDecimal.valueOf(4.966));
        InterestRate.getOrCreate(new LocalDate(2018, 1, 1), new LocalDate(2018, 12, 31), BigDecimal.valueOf(4.857));
    }
}
