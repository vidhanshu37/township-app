INSERT INTO flats (flat_no, tower, area_sqft)
VALUES ('T07-1203', 'T07', 1150),
       ('T12-0408', 'T12', 980),
       ('T21-1601', 'T21', 1640);

INSERT INTO users (name, phone, role, resident_type, flat_id)
VALUES ('Neeraj Kulkarni', '9000000001', 'RESIDENT', 'OWNER',
        (SELECT id FROM flats WHERE flat_no = 'T07-1203')),
       ('Swati Joshi', '9000000002', 'RESIDENT', 'TENANT',
        (SELECT id FROM flats WHERE flat_no = 'T12-0408')),
       ('Aamir Shaikh', '9000000003', 'RESIDENT', 'OWNER',
        (SELECT id FROM flats WHERE flat_no = 'T21-1601'));

INSERT INTO users (name, phone, role)
VALUES ('Help Desk Demo', '9000000010', 'HELP_DESK'),
       ('Committee Demo', '9000000020', 'COMMITTEE');