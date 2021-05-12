package juppislehm;

import robocode.*;
import robocode.util.Utils;
import java.awt.geom.*;     
import java.lang.*;        
import java.util.ArrayList;

/**
 * SurferJupats - BasicSurferi juhendi põhjale ehitatud robot
 * <p>
 * Robot proovib ennustada kuulide liikumist ja neist kõrvale põigata, robot ootab vastase lähenemist, et 
 * talle siis suurelt säru teha :))
 *
 * @author Voidious - BasicSurferi koodimise juhendi autor
 * LINK: https://robowiki.net/wiki/Wave_Surfing_Tutorial suurepärane juhend, mis aitab Wave Surfimisest paremini aimu saada
 * Voidious on wiki lehele kirja pannud, et tema koodi võib igal moel kasutada ja arendada :)
 * @author Mihkel Uutar, Karl Lainestu - relv
 */
public class SurferJupats extends AdvancedRobot {

    public static int BINS = 47;
    public static double _surfStats[] = new double[BINS];
    public Point2D.Double _myLocation;     // Jupatsi asukoht
    public Point2D.Double _enemyLocation;  // vastase >:( asukoht
	
	//Kasutame muutujaid vastase lastud kuulide salvestamiseks
    public ArrayList _enemyWaves;
    public ArrayList _surfDirections;
    public ArrayList _surfAbsBearings;

    // Salvestame vastase EnergyLeveli, et paremini ennustada kuulide liikumist
    public static double _oppEnergy = 100.0;

    // Kasutame Kawigi loodud WallStick meetodit, mis jätab roboti ja seina vahele alati väikese vahe
    public static Rectangle2D.Double _fieldRect
        = new java.awt.geom.Rectangle2D.Double(18, 18, 764, 764);
    public static double WALL_STICK = 160;

	//Peameetod
    public void run() {
        _enemyWaves = new ArrayList();
        _surfDirections = new ArrayList();
        _surfAbsBearings = new ArrayList();

		//Vajalik selleks, et relv pööraks robotist sõltumatult
        setAdjustGunForRobotTurn(true);
		
		//Radari sõltumatu pööramine
        setAdjustRadarForGunTurn(true);
	

        do {
            // Radari kood
            turnRadarRightRadians(Double.POSITIVE_INFINITY);
        } while (true);
    }

    public void onScannedRobot(ScannedRobotEvent e) {
		//Praegune asukoht
        _myLocation = new Point2D.Double(getX(), getY());

		//Muutujad roboti liikumiseks
        double lateralVelocity = getVelocity()*Math.sin(e.getBearingRadians());
		
	
        double absBearing = e.getBearingRadians() + getHeadingRadians();

		//Keeran 
        setTurnRadarRightRadians(Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians()) * 2);

        _surfDirections.add(0,
            new Integer((lateralVelocity >= 0) ? 1 : -1));
        _surfAbsBearings.add(0, new Double(absBearing + Math.PI));


        double bulletPower = _oppEnergy - e.getEnergy();
        if (bulletPower < 3.01 && bulletPower > 0.09
            && _surfDirections.size() > 2) {
            EnemyWave ew = new EnemyWave();
            ew.fireTime = getTime() - 1;
            ew.bulletVelocity = bulletVelocity(bulletPower);
            ew.distanceTraveled = bulletVelocity(bulletPower);
            ew.direction = ((Integer)_surfDirections.get(2)).intValue();
            ew.directAngle = ((Double)_surfAbsBearings.get(2)).doubleValue();
            ew.fireLocation = (Point2D.Double)_enemyLocation.clone(); // last tick

            _enemyWaves.add(ew);
        }

        _oppEnergy = e.getEnergy();

        // update after EnemyWave detection, because that needs the previous
        // enemy location as the source of the wave
        _enemyLocation = project(_myLocation, absBearing, e.getDistance());

        updateWaves();
        doSurfing();

		double radariNurk = getRadarHeadingRadians();
		double relvaNurkPraegu = getGunHeadingRadians();
		double keerataOnVaja = Math.abs(radariNurk - relvaNurkPraegu);
		
		if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
			//Vastasele lähenedes laseb Jupats tugevamalt
			setFire(Math.min(400 / e.getDistance(), 3));
		}		

		if (radariNurk > relvaNurkPraegu) {
			setTurnGunRight(Math.toDegrees(radariNurk - relvaNurkPraegu));
		} else if (radariNurk < relvaNurkPraegu) {
			setTurnGunLeft(Math.toDegrees(relvaNurkPraegu - radariNurk));
		}
    }

    public void updateWaves() {
        for (int x = 0; x < _enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave)_enemyWaves.get(x);

            ew.distanceTraveled = (getTime() - ew.fireTime) * ew.bulletVelocity;
            if (ew.distanceTraveled >
                _myLocation.distance(ew.fireLocation) + 50) {
                _enemyWaves.remove(x);
                x--;
            }
        }
    }

    public EnemyWave getClosestSurfableWave() {
        double closestDistance = 50000; //Mingisugune absurdselt suur arv
        EnemyWave surfWave = null;

        for (int x = 0; x < _enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave)_enemyWaves.get(x);
            double distance = _myLocation.distance(ew.fireLocation)
                - ew.distanceTraveled;

            if (distance > ew.bulletVelocity && distance < closestDistance) {
                surfWave = ew;
                closestDistance = distance;
            }
        }

        return surfWave;
    }

    public static int getFactorIndex(EnemyWave ew, Point2D.Double targetLocation) {
        double offsetAngle = (absoluteBearing(ew.fireLocation, targetLocation)
            - ew.directAngle);
        double factor = Utils.normalRelativeAngle(offsetAngle)
            / maxEscapeAngle(ew.bulletVelocity) * ew.direction;

        return (int)limit(0,
            (factor * ((BINS - 1) / 2)) + ((BINS - 1) / 2),
            BINS - 1);
    }


    public void logHit(EnemyWave ew, Point2D.Double targetLocation) {
        int index = getFactorIndex(ew, targetLocation);

        for (int x = 0; x < BINS; x++) {
            _surfStats[x] += 1.0 / (Math.pow(index - x, 2) + 1);
        }
    }

    public void onHitByBullet(HitByBulletEvent e) {
        if (!_enemyWaves.isEmpty()) {
            Point2D.Double hitBulletLocation = new Point2D.Double(
                e.getBullet().getX(), e.getBullet().getY());
            EnemyWave hitWave = null;

            for (int x = 0; x < _enemyWaves.size(); x++) {
                EnemyWave ew = (EnemyWave)_enemyWaves.get(x);

                if (Math.abs(ew.distanceTraveled -
                    _myLocation.distance(ew.fireLocation)) < 50
                    && Math.abs(bulletVelocity(e.getBullet().getPower()) 
                        - ew.bulletVelocity) < 0.001) {
                    hitWave = ew;
                    break;
                }
            }

            if (hitWave != null) {
                logHit(hitWave, hitBulletLocation);

                _enemyWaves.remove(_enemyWaves.lastIndexOf(hitWave));
            }
        }
    }

    //CREDIT: mini sized predictor from Apollon, by rozu
    //http://robowiki.net?Apollon
    public Point2D.Double predictPosition(EnemyWave surfWave, int direction) {
    	Point2D.Double predictedPosition = (Point2D.Double)_myLocation.clone();
    	double predictedVelocity = getVelocity();
    	double predictedHeading = getHeadingRadians();
    	double maxTurning, moveAngle, moveDir;

        int counter = 0; // number of ticks in the future
        boolean intercepted = false;

    	do {
    		moveAngle =
                wallSmoothing(predictedPosition, absoluteBearing(surfWave.fireLocation,
                predictedPosition) + (direction * (Math.PI/2)), direction)
                - predictedHeading;
    		moveDir = 1;

    		if(Math.cos(moveAngle) < 0) {
    			moveAngle += Math.PI;
    			moveDir = -1;
    		}

    		moveAngle = Utils.normalRelativeAngle(moveAngle);

    		// maxTurning is built in like this, you can't turn more then this in one tick
    		maxTurning = Math.PI/720d*(40d - 3d*Math.abs(predictedVelocity));
    		predictedHeading = Utils.normalRelativeAngle(predictedHeading
                + limit(-maxTurning, moveAngle, maxTurning));

    		// this one is nice ;). if predictedVelocity and moveDir have
            // different signs you want to breack down
    		// otherwise you want to accelerate (look at the factor "2")
    		predictedVelocity += (predictedVelocity * moveDir < 0 ? 2*moveDir : moveDir);
    		predictedVelocity = limit(-8, predictedVelocity, 8);

    		// calculate the new predicted position
    		predictedPosition = project(predictedPosition, predictedHeading, predictedVelocity);

            counter++;

            if (predictedPosition.distance(surfWave.fireLocation) <
                surfWave.distanceTraveled + (counter * surfWave.bulletVelocity)
                + surfWave.bulletVelocity) {
                intercepted = true;
            }
    	} while(!intercepted && counter < 500);

    	return predictedPosition;
    }

    public double checkDanger(EnemyWave surfWave, int direction) {
        int index = getFactorIndex(surfWave,
            predictPosition(surfWave, direction));

        return _surfStats[index];
    }

    public void doSurfing() {
        EnemyWave surfWave = getClosestSurfableWave();

        if (surfWave == null) { return; }

        double dangerLeft = checkDanger(surfWave, -1);
        double dangerRight = checkDanger(surfWave, 1);

        double goAngle = absoluteBearing(surfWave.fireLocation, _myLocation);
        if (dangerLeft < dangerRight) {
            goAngle = wallSmoothing(_myLocation, goAngle - (Math.PI/2), -1);
        } else {
            goAngle = wallSmoothing(_myLocation, goAngle + (Math.PI/2), 1);
        }

        setBackAsFront(this, goAngle);
    }

    class EnemyWave {
        Point2D.Double fireLocation;
        long fireTime;
        double bulletVelocity, directAngle, distanceTraveled;
        int direction;

        public EnemyWave() { }
    }

    // CREDIT: Iterative WallSmoothing by Kawig
    // robowiki.net?WallSmoothing
    public double wallSmoothing(Point2D.Double botLocation, double angle, int orientation) {
        while (!_fieldRect.contains(project(botLocation, angle, 160))) {
            angle += orientation*0.05;
        }
        return angle;
    }

    // CREDIT: from CassiusClay, by PEZ
    // robowiki.net?CassiusClay
    public static Point2D.Double project(Point2D.Double sourceLocation, double angle, double length) {
        return new Point2D.Double(sourceLocation.x + Math.sin(angle) * length,
            sourceLocation.y + Math.cos(angle) * length);
    }

    // CREDIT: RaikoMicro, by Jamougha
    public static double absoluteBearing(Point2D.Double source, Point2D.Double target) {
        return Math.atan2(target.x - source.x, target.y - source.y);
    }

    public static double limit(double min, double value, double max) {
        return Math.max(min, Math.min(value, max));
    }

    public static double bulletVelocity(double power) {
        return (20D - (3D*power));
    }

    public static double maxEscapeAngle(double velocity) {
        return Math.asin(8.0/velocity);
    }

    public static void setBackAsFront(AdvancedRobot robot, double goAngle) {
        double angle =
            Utils.normalRelativeAngle(goAngle - robot.getHeadingRadians());
        if (Math.abs(angle) > (Math.PI/2)) {
            if (angle < 0) {
                robot.setTurnRightRadians(Math.PI + angle);
            } else {
                robot.setTurnLeftRadians(Math.PI - angle);
            }
            robot.setBack(100);
        } else {
            if (angle < 0) {
                robot.setTurnLeftRadians(-1*angle);
           } else {
                robot.setTurnRightRadians(angle);
           }
            robot.setAhead(100);
        }
    }

    public void onPaint(java.awt.Graphics2D g) {
         g.setColor(java.awt.Color.red);
         for(int i = 0; i < _enemyWaves.size(); i++){
            EnemyWave w = (EnemyWave)(_enemyWaves.get(i));
            Point2D.Double center = w.fireLocation;
			
            int radius = (int)w.distanceTraveled;

            if(radius - 40 < center.distance(_myLocation))
               g.drawOval((int)(center.x - radius ), (int)(center.y - radius), radius*2, radius*2);
         }
    }

}