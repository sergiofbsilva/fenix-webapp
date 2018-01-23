package pt.ist.fenix.task;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.fenixedu.academic.domain.ExecutionYear;
import org.fenixedu.academic.domain.accounting.Installment;
import org.fenixedu.academic.domain.accounting.events.AnnualEvent;
import org.fenixedu.academic.domain.accounting.events.gratuity.GratuityEventWithPaymentPlan;
import org.fenixedu.academic.domain.accounting.installments.InstallmentWithMonthlyPenalty;
import org.fenixedu.academic.util.Money;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.fenixedu.commons.spreadsheet.Spreadsheet;
import org.fenixedu.commons.spreadsheet.Spreadsheet.Row;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Interval;
import org.joda.time.LocalDate;

import pt.ist.fenixframework.Atomic.TxMode;

/**
 * Created by Sérgio Silva (hello@fenixedu.org).
 */
public class TestGratuityInterestTask extends CustomTask {
    List<InterestTax> taxes;

    private class InterestTax {
        private final LocalDate start;
        private final LocalDate end;
        private final BigDecimal value;

        public InterestTax(LocalDate start, LocalDate end, BigDecimal value) {
            this.start = start;
            this.end = end;
            this.value = value;
        }

        public Interval getInterval() {
            return new Interval(start.toDateTimeAtStartOfDay(), end.toDateTimeAtStartOfDay().plusDays(1));
        }

        public Integer getNumberOfDays(Interval interval) {
            Interval overlap = getInterval().overlap(interval);
            return overlap == null ? 0 : Days.daysBetween(overlap.getStart(), overlap.getEnd()).getDays();
        }

        public BigDecimal getTax(Interval interval) {
            BigDecimal absoluteTaxValue = value.divide(BigDecimal.valueOf(100), 5, RoundingMode.UNNECESSARY);
            BigDecimal numberOfDays = new BigDecimal(getNumberOfDays(interval));
            BigDecimal numberOfDaysPerYear = BigDecimal.valueOf(365);
            return absoluteTaxValue.multiply(numberOfDays).divide(numberOfDaysPerYear, 5, RoundingMode.UP);
        }

        public Money getInterest(Money amount, Interval interval) {
            return amount.multiply(getTax(interval));
        }
    }

    @Override
    public TxMode getTxMode() {
        return TxMode.READ;
    }

    private String toString(Interval interval) {
        return String.format("[%s,%s[", interval.getStart().toString("dd-MM-yyyy"), interval.getEnd().toString("dd-MM-yyyy"));
    }

    private DateTime getLastInstallmentDueDate(GratuityEventWithPaymentPlan event) {
        return event.getGratuityPaymentPlan().getLastInstallment().getEndDate().toDateTimeAtMidnight();
    }

    private Money getAmount(GratuityEventWithPaymentPlan event, Installment installment, DateTime when) {
        DateTime dueDate = installment.getEndDate().toDateTimeAtMidnight();
        DateTime lastInstallmentDate = getLastInstallmentDueDate(event);
        if (when.isAfter(lastInstallmentDate)) {
            dueDate = lastInstallmentDate;
        }
        if (when.isBefore(dueDate) || when.isEqual(dueDate)) {
            return installment.getAmount();
        } else {
            return getAmountWithInterest(installment.getAmount(), when, dueDate);
        }
    }

    private Money getAmountWithInterest(Money amount, DateTime when, DateTime dueDate) {
        Money sum = amount;
        final Interval dueInterval = new Interval(dueDate.plusDays(1), when.plusDays(1));
        for (InterestTax tax : taxes) {
            Integer taxNumberOfDaysOverlap = tax.getNumberOfDays(dueInterval);
            if (taxNumberOfDaysOverlap > 0) {
                String intervalString = toString(dueInterval);
                String taxIntervalString = toString(tax.getInterval());
                String taxValue = tax.getTax(dueInterval).toPlainString();
                Money interest = tax.getInterest(amount, dueInterval);
                //taskLog("\tdue-period: %s tax-period: %s dueNumberOfDaysOverlap: %s interestRate: %s interestValue: %s%n", intervalString, taxIntervalString, taxNumberOfDaysOverlap, taxValue,
                //interest);
                sum = sum.add(interest);
            }
        }
        return sum;
    }

    String getStudentNumber(GratuityEventWithPaymentPlan event) {
        return event.getPerson().getStudent().getNumber().toString();
    }

    String getStudentName(GratuityEventWithPaymentPlan event) {
        return event.getPerson().getName();
    }

    String getSocialSecurityNumber(GratuityEventWithPaymentPlan event) {
        return event.getPerson().getSocialSecurityNumber();
    }

    String getGratuityExecutionYearName(GratuityEventWithPaymentPlan event) {
        return event.getExecutionYear().getName();
    }

    String getDegreeName(GratuityEventWithPaymentPlan event) {
        return event.getDegree().getNameI18N().getContent();
    }

    String getDegreeTypeName(GratuityEventWithPaymentPlan event) {
        return event.getDegree().getDegreeType().getName().getContent();
    }

    String getWhenOccured(GratuityEventWithPaymentPlan event) {
        return event.getWhenOccured().toString("dd/MM/yyyy");
    }

    boolean isOpen(GratuityEventWithPaymentPlan event, DateTime when) {
        if (event.isCancelled()) {
            return false;
        }

        return event.calculateAmountToPay(when).greaterThan(Money.ZERO);
    }

    @Override
    public void runTask() throws Exception {
        taxes = new ArrayList<InterestTax>();
        Spreadsheet spreadsheet = new Spreadsheet("interest");

        taxes.add(new InterestTax(new LocalDate(2011, 1, 1), new LocalDate(2011, 12, 31), BigDecimal.valueOf(6.351)));
        taxes.add(new InterestTax(new LocalDate(2012, 1, 1), new LocalDate(2012, 12, 31), BigDecimal.valueOf(7.007)));
        taxes.add(new InterestTax(new LocalDate(2013, 1, 1), new LocalDate(2013, 12, 31), BigDecimal.valueOf(6.112)));
        taxes.add(new InterestTax(new LocalDate(2014, 1, 1), new LocalDate(2014, 12, 31), BigDecimal.valueOf(5.535)));
        taxes.add(new InterestTax(new LocalDate(2015, 1, 1), new LocalDate(2015, 12, 31), BigDecimal.valueOf(5.476)));
        taxes.add(new InterestTax(new LocalDate(2016, 1, 1), new LocalDate(2016, 12, 31), BigDecimal.valueOf(5.168)));
        taxes.add(new InterestTax(new LocalDate(2017, 1, 1), new LocalDate(2017, 12, 31), BigDecimal.valueOf(4.966)));
        taxes.add(new InterestTax(new LocalDate(2018, 1, 1), new LocalDate(2018, 12, 31), BigDecimal.valueOf(4.857)));

        DateTime when = new DateTime();
        taskLog("when: %s%n", when.toString());
        ExecutionYear executionYear = ExecutionYear.readCurrentExecutionYear();
        taskLog("current: %s%n", executionYear.getQualifiedName());
        Set<GratuityEventWithPaymentPlan> collect =
            executionYear.getAnnualEventsSet().stream().filter(GratuityEventWithPaymentPlan.class::isInstance).map(GratuityEventWithPaymentPlan.class::cast)
                .filter(e -> !e.isCancelled()).filter(e -> e.getPerson().getUsername().equalsIgnoreCase("ist175663")).collect(Collectors.toSet());

        for (AnnualEvent e : collect) {
            GratuityEventWithPaymentPlan event = (GratuityEventWithPaymentPlan) e;
            String studentName = getStudentName(event);
            String studentNumber = getStudentNumber(event);
            String gratuityExecutionYearName = getGratuityExecutionYearName(event);
            String degreeName = getDegreeName(event);
            String degreeTypeName = getDegreeTypeName(event);
            String whenOccured = getWhenOccured(event);
            taskLog("%s %s %s%n", event.getExternalId() , event.getDescription(), studentNumber);

            Money totalAmountToPay = event.getTotalAmountToPay(when);
            Money amountToPay = event.calculateAmountToPay(when);

            Money newTotalAmountToPay = Money.ZERO;

            List<Installment> installmentsSet = event.getGratuityPaymentPlan().getInstallmentsSortedByEndDate();
            boolean error = false;

            for (Installment i : installmentsSet) {
                if (i.getClass() != InstallmentWithMonthlyPenalty.class) {
                    taskLog("ERROR: no monthy penalty event: %s installment %s%n", event.getExternalId(), i.getExternalId());
                    error = true;
                    break;
                }
                newTotalAmountToPay = newTotalAmountToPay.add(getAmount(event, i, when));
            }

            if (error) {
                continue;
            }

            Money newAmountToPay = newTotalAmountToPay.subtract(event.getPayedAmount(when));
            Money differenceAmount = amountToPay.subtract(newAmountToPay);

            Row row = spreadsheet.addRow();
            row.setCell("Name", studentName);
            row.setCell("Number", studentNumber);
            row.setCell("executionYear", gratuityExecutionYearName);
            row.setCell("degree", degreeName);
            row.setCell("degreeType", degreeTypeName);
            row.setCell("whenOccured", whenOccured);
            row.setCell("totalAmountToPay", totalAmountToPay.toPlainString());
            row.setCell("amountInDebt", amountToPay.toPlainString());
            row.setCell("isOpen", Boolean.toString(event.isOpen()));
            row.setCell("newTotalAmountToPay", newTotalAmountToPay.toPlainString());
            row.setCell("newAmountInDebt", newAmountToPay.toPlainString());
            row.setCell("newIsOpen", Boolean.toString(event.isOpen()));
            row.setCell("delta", differenceAmount.toPlainString());
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        spreadsheet.exportToXLSSheet(baos);
        output("taxes.csv", baos.toByteArray());

    }
}
