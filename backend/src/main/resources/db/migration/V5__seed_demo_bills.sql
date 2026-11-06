INSERT INTO bills (flat_id, period, amount, due_date)
SELECT f.id, date_trunc('month', d.due)::date, f.area_sqft * 6.50, d.due
FROM flats f
         JOIN (VALUES ('T07-1203', date_trunc('month', current_date)::date + 9),
                      ('T12-0408', current_date - 47),
                      ('T21-1601', current_date - 18)) AS d(flat_no, due) ON d.flat_no = f.flat_no;


INSERT INTO ledger_entry (flat_id, bill_id, entry_type, amount, reference)
SELECT flat_id, id, 'BILL', amount, 'BILL-' || to_char(period, 'YYYY-MM')
FROM bills;



INSERT INTO ledger_entry (flat_id, bill_id, entry_type, amount, reference)
SELECT b.flat_id, b.id, 'PAYMENT', -p.paid, 'SEED-PAY-' || f.flat_no
FROM bills b
         JOIN flats f ON f.id = b.flat_id
         JOIN (VALUES ('T07-1203', 7475.00), ('T21-1601', 5000.00)) AS p(flat_no, paid)
              ON p.flat_no = f.flat_no;