import { Router } from 'express';
import { search } from '../services/youtube.js';

const router = Router();

router.get('/', async (req, res, next) => {
    try {
        const { q } = req.query;
        if (!q) {
            return res.status(400).json({ error: 'Missing query parameter q' });
        }
        const results = await search(q);
        res.json(results);
    } catch (err) {
        next(err);
    }
});

export default router;
